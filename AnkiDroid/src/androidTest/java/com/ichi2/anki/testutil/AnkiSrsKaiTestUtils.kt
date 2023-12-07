package com.ichi2.anki.testutil

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.ichi2.anki.R
import com.ichi2.libanki.Card
import com.ichi2.libanki.Consts
import com.ichi2.libanki.utils.TimeManager
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class AnkiSrsKaiTestUtils private constructor() {
    companion object {
        fun closeGetStartedScreenIfExists() {
            onView(withId(R.id.get_started))
                .withFailureHandler { _, _ -> }
                .perform(click())
        }

        fun closeBackupCollectionDialogIfExists() {
            onView(withText(R.string.button_backup_later))
                .withFailureHandler { _, _ -> }
                .perform(click())
        }

        private fun clickOnDeckWithName(deckName: String) {
            onView(withId(R.id.files)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText(deckName)),
                    click()
                )
            )
        }

        private fun clickOnStudyButtonIfExists() {
            onView(withId(R.id.studyoptions_start))
                .withFailureHandler { _, _ -> }
                .perform(click())
        }

        fun reviewDeckWithName(deckName: String) {
            onView(withId(R.id.files)).checkWithTimeout(matches(hasDescendant(withText(deckName))))
            clickOnDeckWithName(deckName)
            // Adding cards directly to the database while in the Deck Picker screen
            // will not update the page with correct card counts. Hence, clicking
            // on the deck will bring us to the study options page where we need to
            // click on the Study button. If we have added cards to the database
            // before the Deck Picker screen has fully loaded, then we skip clicking
            // the Study button
            clickOnStudyButtonIfExists()
        }

        fun rebuildFilteredDeck(deckName: String) {
            onView(withId(R.id.files)).checkWithTimeout(matches(hasDescendant(withText(deckName))))
            onView(withId(R.id.files)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText(deckName)),
                    longClick()
                )
            )
            onView(withId(com.afollestad.materialdialogs.R.id.md_recyclerview_content))
                .checkWithTimeout(matches(hasDescendant(withText("Rebuild"))))
            onView(withId(com.afollestad.materialdialogs.R.id.md_recyclerview_content))
                .perform(
                    RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                        hasDescendant(withText("Rebuild")),
                        click()
                    )
                )
        }

        fun clickShowAnswerAndAnswerAgain() {
            onView(withId(R.id.flashcard_layout_flip))
                .perform(click())
            // We need to wait for the card to fully load to allow enough time for
            // the messages to be passed in and out of the WebView when evaluating
            // the custom JS scheduler code. The ease buttons are hidden until the
            // custom scheduler has finished running
            onView(withId(R.id.flashcard_layout_ease1))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_ease1))
                .perform(click())
        }

        fun clickShowAnswerAndAnswerHard() {
            onView(withId(R.id.flashcard_layout_flip))
                .perform(click())
            // We need to wait for the card to fully load to allow enough time for
            // the messages to be passed in and out of the WebView when evaluating
            // the custom JS scheduler code. The ease buttons are hidden until the
            // custom scheduler has finished running
            onView(withId(R.id.flashcard_layout_ease2))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_ease2))
                .perform(click())
        }

        fun clickShowAnswerAndAnswerGood() {
            onView(withId(R.id.flashcard_layout_flip))
                .perform(click())
            // We need to wait for the card to fully load to allow enough time for
            // the messages to be passed in and out of the WebView when evaluating
            // the custom JS scheduler code. The ease buttons are hidden until the
            // custom scheduler has finished running
            onView(withId(R.id.flashcard_layout_ease3))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_ease3))
                .perform(click())
        }

        fun clickShowAnswerAndAnswerEasy() {
            onView(withId(R.id.flashcard_layout_flip))
                .perform(click())
            // We need to wait for the card to fully load to allow enough time for
            // the messages to be passed in and out of the WebView when evaluating
            // the custom JS scheduler code. The ease buttons are hidden until the
            // custom scheduler has finished running
            onView(withId(R.id.flashcard_layout_ease4))
                .checkWithTimeout(matches(isDisplayed()))
            onView(withId(R.id.flashcard_layout_ease4))
                .perform(click())
        }

        fun Card.moveToLearnQueue() {
            this.queue = Consts.QUEUE_TYPE_LRN
            this.type = Consts.CARD_TYPE_LRN
            this.due = 0
            this.col.updateCard(this, true)
        }

        fun Card.moveToRelearnQueue() {
            this.queue = Consts.QUEUE_TYPE_LRN
            this.type = Consts.CARD_TYPE_RELEARNING
            this.due = 0
            this.col.updateCard(this, true)
        }

        private fun ViewInteraction.checkWithTimeout(
            viewAssertion: ViewAssertion,
            retryWaitTimeInMilliseconds: Long = 100,
            maxWaitTimeInMilliseconds: Long = TimeUnit.SECONDS.toMillis(10)
        ) {
            val startTime = TimeManager.time.intTimeMS()

            while (TimeManager.time.intTimeMS() - startTime < maxWaitTimeInMilliseconds) {
                try {
                    check(viewAssertion)
                    return
                } catch (e: Throwable) {
                    Thread.sleep(retryWaitTimeInMilliseconds)
                }
            }

            fail("View assertion was not true within $maxWaitTimeInMilliseconds milliseconds")
        }
    }
}
