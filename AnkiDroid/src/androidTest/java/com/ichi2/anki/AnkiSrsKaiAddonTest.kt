package com.ichi2.anki

import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import anki.cards.FsrsMemoryState
import anki.deck_config.DeckConfigsForUpdate
import anki.decks.Deck
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.clickShowAnswerAndAnswerAgain
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.clickShowAnswerAndAnswerEasy
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.clickShowAnswerAndAnswerGood
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.closeBackupCollectionDialogIfExists
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.closeGetStartedScreenIfExists
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.moveToLearnQueue
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.moveToRelearnQueue
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.rebuildFilteredDeck
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.reviewDeckWithName
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Storage
import com.ichi2.libanki.utils.TimeManager
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiSrsKaiAddonTest : InstrumentedTest() {

    // Launch IntroductionActivity instead of DeckPicker activity because in CI
    // builds, it seems to create IntroductionActivity after the DeckPicker,
    // causing the DeckPicker activity to be destroyed. As a consequence, this
    // will throw RootViewWithoutFocusException when Espresso tries to interact
    // with an already destroyed activity. By launching IntroductionActivity, we
    // ensure that IntroductionActivity is launched first and navigate to the
    // DeckPicker -> Reviewer activities
    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(IntroductionActivity::class.java)

    @get:Rule
    val mRuntimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun setup() {
        Storage.setUseInMemory(true)
    }

    @Test
    fun existingCustomDataIsUpdatedWithCorrectSuccessCount() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", "customData.good.c = 0;")
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("""{"c":0}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":0}"}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":1}"}""")
        )
    }

    @Test
    fun nonExistentCustomDataIsUpdatedWithCorrectSuccessCount() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":1}"}""")
        )
    }

    @Test
    fun ensureFsrsAndExistingCustomDataAreNotLost() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        val deckConfig = col.backend.getDeckConfig(deck.id)
        col.backend.updateDeckConfigs(
            deck.id,
            listOf(deckConfig),
            emptyList(),
            applyToChildren = true,
            cardStateCustomizer = "",
            DeckConfigsForUpdate.CurrentDeck.Limits
                .newBuilder()
                .setNew(100)
                .setNewToday(100)
                .setNewTodayActive(true)
                .setReview(100)
                .setReviewToday(100)
                .setReviewTodayActive(true)
                .build(),
            newCardsIgnoreReviewLimit = true,
            fsrs = true
        )
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)
        col.backend.updateCards(
            listOf(
                card.toBackendCard()
                    .toBuilder()
                    .setCustomData("""{"test":100}""")
                    .setMemoryState(
                        FsrsMemoryState
                            .newBuilder()
                            .setDifficulty(5.0F)
                            .setStability(100.0F)
                    )
                    .setDesiredRetention(0.90F)
                    .build()
            ),
            true
        )

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        // AnkiDroid's Card class currently doesn't contain all the fields, so
        // we have to use the protobuf-generated Card class
        var cardFromDb = col.backend.getCard(card.id)
        assertThat(
            cardFromDb,
            equalTo(
                cardFromDb
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("""{"test":100}""")
                    .setMemoryState(
                        FsrsMemoryState
                            .newBuilder()
                            .setDifficulty(5.0F)
                            .setStability(100.0F)
                    )
                    .setDesiredRetention(0.90F)
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"s":100.0,"d":5.0,"dr":0.9,"cd":"{\"test\":100}"}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.backend.getCard(card.id)
        assertThat(
            cardFromDb,
            equalTo(
                cardFromDb
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("""{"test":100}""")
                    .setMemoryState(
                        FsrsMemoryState
                            .newBuilder()
                            .setDifficulty(5.0F)
                            .setStability(100.0F)
                    )
                    .setDesiredRetention(0.90F)
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"s":100.0,"d":5.0,"dr":0.9,"cd":"{\"test\":100}"}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id).toBackendCard()
        assertThat(
            cardFromDb,
            equalTo(
                cardFromDb
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("""{"test":100,"c":1}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"s":100.0,"d":5.0,"dr":0.9,"cd":"{\"test\":100,\"c\":1}"}""")
        )
    }

    @Test
    fun newAndLearnAndRelearnCardsAreIgnored() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val newNote = addNoteUsingBasicModel("foo", "bar")
        val newCard = newNote.firstCard()
        val learnNote = addNoteUsingBasicModel("foo", "bar")
        val learnCard = learnNote.firstCard()
        learnCard.moveToLearnQueue()
        val relearnNote = addNoteUsingBasicModel("foo", "bar")
        val relearnCard = relearnNote.firstCard()
        relearnCard.moveToRelearnQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()

        var newCardFromDb = col.getCard(newCard.id)
        assertThat(
            newCardFromDb.toBackendCard(),
            equalTo(
                newCardFromDb.toBackendCard()
                    .toBuilder()
                    .setCtype(Consts.CARD_TYPE_NEW)
                    .setQueue(Consts.QUEUE_TYPE_NEW)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${newCardFromDb.id};"),
            equalTo("""{}""")
        )
        var learnCardFromDb = col.getCard(learnCard.id)
        assertThat(
            learnCardFromDb.toBackendCard(),
            equalTo(
                learnCardFromDb.toBackendCard()
                    .toBuilder()
                    .setCtype(Consts.CARD_TYPE_LRN)
                    .setQueue(Consts.QUEUE_TYPE_LRN)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${learnCardFromDb.id};"),
            equalTo("""{}""")
        )
        var relearnCardFromDb = col.getCard(relearnCard.id)
        assertThat(
            relearnCardFromDb.toBackendCard(),
            equalTo(
                relearnCardFromDb.toBackendCard()
                    .toBuilder()
                    .setCtype(Consts.CARD_TYPE_RELEARNING)
                    .setQueue(Consts.QUEUE_TYPE_LRN)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${relearnCardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        newCardFromDb = col.getCard(newCard.id)
        assertThat(
            newCardFromDb.toBackendCard(),
            equalTo(
                newCardFromDb.toBackendCard()
                    .toBuilder()
                    .setCtype(Consts.CARD_TYPE_NEW)
                    .setQueue(Consts.QUEUE_TYPE_NEW)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${newCardFromDb.id};"),
            equalTo("{}")
        )
        learnCardFromDb = col.getCard(learnCard.id)
        assertThat(
            learnCardFromDb.toBackendCard(),
            equalTo(
                learnCardFromDb.toBackendCard()
                    .toBuilder()
                    .setCtype(Consts.CARD_TYPE_LRN)
                    .setQueue(Consts.QUEUE_TYPE_LRN)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${learnCardFromDb.id};"),
            equalTo("{}")
        )
        relearnCardFromDb = col.getCard(relearnCard.id)
        assertThat(
            relearnCardFromDb.toBackendCard(),
            equalTo(
                relearnCardFromDb.toBackendCard()
                    .toBuilder()
                    .setCtype(Consts.CARD_TYPE_RELEARNING)
                    .setQueue(Consts.QUEUE_TYPE_LRN)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${relearnCardFromDb.id};"),
            equalTo("{}")
        )
    }

    @Test
    fun goodSuccessfulReviewCountIsZeroWhenTheAgainButtonWasLastPressed() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        pressBack()
        // Finish reviewing the card after pressing Again to move it to the
        // Review state, since we only update review cards
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("""{"c":0}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":0}"}""")
        )
    }

    @Test
    fun goodSuccessfulReviewCountIsCorrectWhenAgainIsPressedAndSuccessfulReviewsExistAfter() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(4)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(5)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(5)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun goodSuccessfulReviewCountIsCorrectWhenTheAgainButtonWasNeverPressed() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun easySuccessfulReviewCountIsZeroWhenTheAgainButtonWasLastPressed() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        pressBack()
        // Finish reviewing the card after pressing Again to move it to the
        // Review state, since we only update review cards
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("""{"c":0}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":0}"}""")
        )
    }

    @Test
    fun easySuccessfulReviewCountIsCorrectWhenAgainIsPressedAndSuccessfulReviewsExistAfter() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(4)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(5)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(5)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun easySuccessfulReviewCountIsCorrectWhenTheAgainButtonWasNeverPressed() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckGoodSuccessfulReviewCountIsZeroWhenTheAgainButtonWasLastPressed() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        card.moveToReviewQueue()

        val filteredDeckId = col.decks.newDyn("Filtered Deck " + TimeManager.time.intTimeMS())
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        pressBack()
        // Finish reviewing the card after pressing Again to move it to the
        // Review state, since we only update review cards
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("""{"c":0}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":0}"}""")
        )
    }

    @Test
    fun filteredDeckGoodSuccessfulReviewCountIsCorrectWhenAgainIsPressedAndSuccessfulReviewsExistAfter() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        card.moveToReviewQueue()

        val filteredDeckId = col.decks.newDyn("Filtered Deck " + TimeManager.time.intTimeMS())
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(4)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(5)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(5)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckGoodSuccessfulReviewCountIsCorrectWhenTheAgainButtonWasNeverPressed() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        card.moveToReviewQueue()

        val filteredDeckId = col.decks.newDyn("Filtered Deck " + TimeManager.time.intTimeMS())
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckEasySuccessfulReviewCountIsZeroWhenTheAgainButtonWasLastPressed() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        card.moveToReviewQueue()

        val filteredDeckId = col.decks.newDyn("Filtered Deck " + TimeManager.time.intTimeMS())
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerAgain()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(3)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(4)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(5)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(5)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    @Test
    fun filteredDeckEasySuccessfulReviewCountIsCorrectWhenTheAgainButtonWasNeverPressed() {
        val sql = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SQL_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        card.moveToReviewQueue()

        val filteredDeckId = col.decks.newDyn("Filtered Deck " + TimeManager.time.intTimeMS())
        val filteredDeck = col.backend.getOrCreateFilteredDeck(filteredDeckId)
            .toBuilder()
            .setConfig(
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(true)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch("(is:due OR is:learn is:review)")
                            .setLimit(100)
                            .setOrder(Deck.Filtered.SearchTerm.Order.RANDOM)
                            .build()
                    )
            )
            .build()
        col.backend.addOrUpdateFilteredDeck(filteredDeck)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(filteredDeck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )
        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        rebuildFilteredDeck(filteredDeck.name)
        reviewDeckWithName(filteredDeck.name)
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{}""")
        )

        col.db.execute(sql)

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setReps(2)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )
        assertThat(
            col.db.queryString("SELECT data from CARDS where id = ${cardFromDb.id};"),
            equalTo("""{"cd":"{\"c\":2}"}""")
        )
    }

    // Add test for all 3 revlog cases where we press good for filtered deck
    // Add test for all 3 revlog cases where we press easy for filtered deck

    companion object {
        private const val SQL_FILE_NAME = "anki_srs_kai_addon.sql"
    }
}
