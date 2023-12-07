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
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.clickShowAnswerAndAnswerHard
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.closeBackupCollectionDialogIfExists
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.closeGetStartedScreenIfExists
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.rebuildFilteredDeck
import com.ichi2.anki.testutil.AnkiSrsKaiTestUtils.Companion.reviewDeckWithName
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Storage
import com.ichi2.libanki.sched.CurrentQueueState
import com.ichi2.libanki.utils.TimeManager
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AnkiSrsKaiIntegrationTest : InstrumentedTest() {

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
    fun easeRewardCounterIsResetWhenPressingAgain() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(300)
                    .setInterval(300)
                    .setEaseFactor(2050)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerAgain()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setCtype(Consts.CARD_TYPE_RELEARNING)
                    .setQueue(Consts.QUEUE_TYPE_LRN)
                    .setDue(cardFromDb.toBackendCard().due)
                    .setInterval(1)
                    .setEaseFactor(1850)
                    .setReps(2)
                    .setLapses(1)
                    .setRemainingSteps(1)
                    .setCustomData("")
                    .build()
            )
        )

        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(1)
                    .setInterval(1)
                    .setEaseFactor(1850)
                    .setReps(3)
                    .setLapses(1)
                    .setCustomData("")
                    .build()
            )
        )
    }

    @Test
    fun easeRewardIsNotAppliedWhenPressingHard() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )

        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(200)
                    .setInterval(200)
                    .setEaseFactor(1850)
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(400)
                    .setInterval(400)
                    .setEaseFactor(1700)
                    .setReps(2)
                    .setCustomData("")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(800)
                    .setInterval(800)
                    .setEaseFactor(1550)
                    .setReps(3)
                    .setCustomData("")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(1600)
                    .setInterval(1600)
                    .setEaseFactor(1400)
                    .setReps(4)
                    .setCustomData("")
                    .build()
            )
        )
    }

    @Test
    fun easeRewardIsAppliedWhenPressingGood() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(300)
                    .setInterval(300)
                    .setEaseFactor(2000)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(900)
                    .setInterval(900)
                    .setEaseFactor(2000)
                    .setReps(2)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(2700)
                    .setInterval(2700)
                    .setEaseFactor(2050)
                    .setReps(3)
                    .setCustomData("""{"c":3}""")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(8100)
                    .setInterval(8100)
                    .setEaseFactor(2150)
                    .setReps(4)
                    .setCustomData("""{"c":4}""")
                    .build()
            )
        )
    }

    @Test
    fun easeRewardIsAppliedWhenPressingEasy() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 3.00,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )

        clickShowAnswerAndAnswerEasy()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(400)
                    .setInterval(400)
                    .setEaseFactor(2150)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(1600)
                    .setInterval(1600)
                    .setEaseFactor(2300)
                    .setReps(2)
                    .setCustomData("""{"c":2}""")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(6400)
                    .setInterval(6400)
                    .setEaseFactor(2500)
                    .setReps(3)
                    .setCustomData("""{"c":3}""")
                    .build()
            )
        )

        col.getCard(card.id).moveToReviewQueue()
        pressBack()
        reviewDeckWithName(deck.name)
        clickShowAnswerAndAnswerEasy()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(25600)
                    .setInterval(25600)
                    .setEaseFactor(2750)
                    .setReps(4)
                    .setCustomData("""{"c":4}""")
                    .build()
            )
        )
    }

    @Test
    fun multipliersWithCustomFunctionAndEaseReward() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                // Function chosen such that f(100) = 2.0
                return (currentEaseFactor / Math.pow(currentInterval, 0.10)) + 0.7381;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                // Function chosen such that f(100) = 3.0
                return (currentEaseFactor / Math.pow(currentInterval, 0.10)) + 1.7381;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                // Function chosen such that f(100) = 4.0
                return (currentEaseFactor / Math.pow(currentInterval, 0.10)) + 2.7381;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val hardNote = addNoteUsingBasicModel("foo", "hard")
        val hardCard = hardNote.firstCard()
        val deck = col.decks.get(hardNote.notetype.did)!!
        hardCard.moveToReviewQueue()
        hardCard.factor = 2000
        hardCard.ivl = 100
        col.updateCard(hardCard, skipUndoEntry = true)

        val goodNote = addNoteUsingBasicModel("foo", "good")
        val goodCard = goodNote.firstCard()
        goodCard.moveToReviewQueue()
        goodCard.factor = 2000
        goodCard.ivl = 100
        col.updateCard(goodCard, skipUndoEntry = true)

        val easyNote = addNoteUsingBasicModel("foo", "easy")
        val easyCard = easyNote.firstCard()
        easyCard.moveToReviewQueue()
        easyCard.factor = 2000
        easyCard.ivl = 100
        col.updateCard(easyCard, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        val queuedCards = col.backend.getQueuedCards(100, false).cardsList

        var cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerHard()
        cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(200)
                    .setInterval(200)
                    .setEaseFactor(1850)
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )

        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(300)
                    .setInterval(300)
                    .setEaseFactor(2050)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )

        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(400)
                    .setInterval(400)
                    .setEaseFactor(2200)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )
    }

    @Test
    fun ensureFsrsAndExistingCustomDataAreNotLost() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
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
            deckOptions + scheduler,
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
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
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

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.backend.getCard(card.id)
        assertThat(
            cardFromDb,
            equalTo(
                cardFromDb
                    .toBuilder()
                    .setDue(300)
                    .setInterval(300)
                    .setEaseFactor(2050)
                    .setReps(1)
                    .setCustomData("""{"test":100,"c":1}""")
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
    }

    @Test
    fun turningOffAllSettingsPreservesOriginalAnkiSrsBehaviour() {
        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 0,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val hardNote = addNoteUsingBasicModel("foo", "hard")
        val hardCard = hardNote.firstCard()
        val deck = col.decks.get(hardNote.notetype.did)!!
        hardCard.moveToReviewQueue()
        hardCard.factor = 2000
        hardCard.ivl = 100
        col.updateCard(hardCard, skipUndoEntry = true)

        val goodNote = addNoteUsingBasicModel("foo", "good")
        val goodCard = goodNote.firstCard()
        goodCard.moveToReviewQueue()
        goodCard.factor = 2000
        goodCard.ivl = 100
        col.updateCard(goodCard, skipUndoEntry = true)

        val easyNote = addNoteUsingBasicModel("foo", "easy")
        val easyCard = easyNote.firstCard()
        easyCard.moveToReviewQueue()
        easyCard.factor = 2000
        easyCard.ivl = 100
        col.updateCard(easyCard, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        val queuedCards = col.backend.getQueuedCards(100, false).cardsList

        var schedulingStates = col.backend.getSchedulingStates(queuedCards[0].card.id)
        var cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerHard()

        cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(schedulingStates.hard.normal.review.scheduledDays)
                    .setInterval(schedulingStates.hard.normal.review.scheduledDays)
                    .setEaseFactor(1850)
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )

        schedulingStates = col.backend.getSchedulingStates(queuedCards[1].card.id)
        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(schedulingStates.good.normal.review.scheduledDays)
                    .setInterval(schedulingStates.good.normal.review.scheduledDays)
                    .setEaseFactor(2000)
                    .setReps(1)
                    // The number of successful reviews should still be counted
                    // so that when the custom scheduler is turned back on, the
                    // counter will be accurate
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )

        schedulingStates = col.backend.getSchedulingStates(queuedCards[2].card.id)
        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(schedulingStates.easy.normal.review.scheduledDays)
                    .setInterval(schedulingStates.easy.normal.review.scheduledDays)
                    .setEaseFactor(2150)
                    .setReps(1)
                    // The number of successful reviews should still be counted
                    // so that when the custom scheduler is turned back on, the
                    // counter will be accurate
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )
    }

    @Test
    fun enableFuzz() {
        val activityMonitor = InstrumentationRegistry
            .getInstrumentation()
            .addMonitor(Reviewer::class.java.name, null, false)

        val deckOptions = """
const deckOptions = {
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        // CI builds can be very slow, so we set the timeout to 30 seconds
        val activity =
            (activityMonitor.waitForActivityWithTimeout(TimeUnit.SECONDS.toMillis(30)) as Reviewer?)!!
        activity.queueState = CurrentQueueState(
            activity.queueState!!.topCard,
            activity.queueState!!.countsIndex,
            activity.queueState!!.states,
            activity.queueState!!.context.toBuilder().setSeed(123).build(),
            activity.queueState!!.counts,
            activity.queueState!!.timeboxReached,
            activity.queueState!!.learnAheadSecs,
            activity.queueState!!.customSchedulingJs
        )

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(289)
                    .setInterval(289)
                    .setEaseFactor(2050)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )
    }

    @Test
    fun customDeckOptions() {
        val deckOptions = """
const deckOptions = {
    "Default": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 6.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
        },
    },
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                card.toBackendCard()
                    .toBuilder()
                    .setDue(600)
                    .setInterval(600)
                    .setEaseFactor(2050)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )
    }

    @Test
    fun filteredDeck() {
        val deckOptions = """
const deckOptions = {
    "Default": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 5.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 6.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 7.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val hardNote = addNoteUsingBasicModel("foo", "hard")
        val hardCard = hardNote.firstCard()
        hardCard.moveToReviewQueue()
        hardCard.factor = 2000
        hardCard.ivl = 100
        col.updateCard(hardCard, skipUndoEntry = true)

        val goodNote = addNoteUsingBasicModel("foo", "good")
        val goodCard = goodNote.firstCard()
        goodCard.moveToReviewQueue()
        goodCard.factor = 2000
        goodCard.ivl = 100
        col.updateCard(goodCard, skipUndoEntry = true)

        val easyNote = addNoteUsingBasicModel("foo", "easy")
        val easyCard = easyNote.firstCard()
        easyCard.moveToReviewQueue()
        easyCard.factor = 2000
        easyCard.ivl = 100
        col.updateCard(easyCard, skipUndoEntry = true)

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

        val queuedCards = col.backend.getQueuedCards(100, false).cardsList

        var cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerHard()
        cardFromDb = col.getCard(queuedCards[0].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(200)
                    .setInterval(200)
                    .setEaseFactor(1850)
                    .setReps(1)
                    .setCustomData("")
                    .build()
            )
        )

        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerGood()
        cardFromDb = col.getCard(queuedCards[1].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(300)
                    .setInterval(300)
                    .setEaseFactor(2050)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )

        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )
        clickShowAnswerAndAnswerEasy()
        cardFromDb = col.getCard(queuedCards[2].card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(400)
                    .setInterval(400)
                    .setEaseFactor(2200)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
        )
    }

    @Test
    fun filteredDeckEaseRewardIsResetWhenPressingAgain() {
        val deckOptions = """
const deckOptions = {
    "Default": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 1,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 2.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 3.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 4.0;
            },
        },
    },
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: false,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 5.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return 6.0;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 7.0;
            },
        },
    },
};
        """
        val scheduler = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(SCHEDULER_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        col.config.set("cardStateCustomizer", deckOptions + scheduler)
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        card.moveToReviewQueue()
        card.factor = 2000
        card.ivl = 100
        col.updateCard(card, skipUndoEntry = true)

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
                    .setDue(0)
                    .setInterval(100)
                    .setEaseFactor(2000)
                    .setReps(0)
                    .setCustomData("")
                    .build()
            )
        )

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id)
        assertThat(
            cardFromDb.toBackendCard(),
            equalTo(
                cardFromDb.toBackendCard()
                    .toBuilder()
                    .setDue(300)
                    .setInterval(300)
                    .setEaseFactor(2050)
                    .setReps(1)
                    .setCustomData("""{"c":1}""")
                    .build()
            )
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
                    .setCtype(Consts.CARD_TYPE_RELEARNING)
                    .setQueue(Consts.QUEUE_TYPE_LRN)
                    .setDue(cardFromDb.toBackendCard().due)
                    .setInterval(1)
                    .setEaseFactor(1850)
                    .setReps(2)
                    .setLapses(1)
                    .setRemainingSteps(1)
                    .setCustomData("")
                    .build()
            )
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
                    .setDue(1)
                    .setInterval(1)
                    .setEaseFactor(1850)
                    .setReps(3)
                    .setLapses(1)
                    .setCustomData("")
                    .build()
            )
        )
    }

    companion object {
        private const val SCHEDULER_FILE_NAME = "anki_srs_kai.js"
    }
}
