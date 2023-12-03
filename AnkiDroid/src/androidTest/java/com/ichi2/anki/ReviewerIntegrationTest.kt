package com.ichi2.anki

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.libanki.Card
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewerIntegrationTest : InstrumentedTest() {

    @get:Rule
    val mRuntimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    private fun moveToReviewQueue(reviewCard: Card) {
        reviewCard.queue = Consts.QUEUE_TYPE_REV
        reviewCard.type = Consts.CARD_TYPE_REV
        reviewCard.due = 0
        col.updateCard(reviewCard, skipUndoEntry = true)
    }

    private fun addNoteUsingBasicModel(front: String, back: String): Note {
        return addNoteUsingModelName("Basic", front, back)
    }

    private fun addNoteUsingModelName(name: String?, vararg fields: String): Note {
        val model = col.notetypes.byName((name)!!)
            ?: throw IllegalArgumentException("Could not find model '$name'")
        // PERF: if we modify newNote(), we can return the card and return a Pair<Note, Card> here.
        // Saves a database trip afterwards.
        val n = col.newNote(model)
        for ((i, field) in fields.withIndex()) {
            n.setField(i, field)
        }
        check(col.addNote(n) != 0) { "Could not add note: {${fields.joinToString(separator = ", ")}}" }
        return n
    }

    @Test
    fun testCustomSchedulerWithCustomData() {
        col.config.set(
            "cardStateCustomizer",
            """
            console.log(JSON.stringify(customData));
            console.log(JSON.stringify(ctx));
            console.log(JSON.stringify(states));
            customData.good.c += 1;
            console.log(JSON.stringify(customData));
            console.log(JSON.stringify(ctx));
            console.log(JSON.stringify(states));
        """
        )
        val note = addNoteUsingBasicModel("1", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        moveToReviewQueue(card)
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)
        val backendCard = anki.cards.Card.newBuilder()
            .setId(card.id)
            .setNoteId(card.nid)
            .setDeckId(card.did)
            .setTemplateIdx(card.ord)
            .setCtype(card.type)
            .setQueue(card.queue)
            .setDue(card.due.toInt())
            .setInterval(card.ivl)
            .setEaseFactor(card.factor)
            .setReps(card.reps)
            .setLapses(card.lapses)
            .setRemainingSteps(card.left)
            .setOriginalDue(card.oDue.toInt())
            .setOriginalDeckId(card.oDid)
            .setFlags(card.userFlag())
            .setCustomData("""{"c":1}""")
            .build()
        col.backend.updateCards(listOf(backendCard), true)

        ActivityScenario.launch(DeckPicker::class.java).use {
            onView(withId(R.id.get_started)).withFailureHandler { _, _ -> }.perform(click())
            onView(withText(R.string.button_backup_later))
                .withFailureHandler { _, _ -> }
                .perform(click())
            onView(withId(R.id.files)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText(deck.name)),
                    click()
                )
            )

            var cardFromDb = col.getCard(card.id).toBackendCard()
            assertThat(cardFromDb.customData, equalTo("""{"c":1}"""))

            Thread.sleep(1000)
            onView(withId(R.id.flashcard_layout_flip)).perform(click())
            onView(withId(R.id.flashcard_layout_ease3)).perform(click())

            cardFromDb = col.getCard(card.id).toBackendCard()
            assertThat(cardFromDb.customData, equalTo("""{"c":2}"""))
        }
    }
}
