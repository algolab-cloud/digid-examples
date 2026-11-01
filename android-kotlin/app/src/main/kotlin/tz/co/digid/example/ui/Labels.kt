package tz.co.digid.example.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import tz.co.digid.example.R
import tz.co.digid.example.ui.theme.Tone
import tz.co.digid.sdk.CardType
import tz.co.digid.sdk.CheckId
import tz.co.digid.sdk.CheckStatus
import tz.co.digid.sdk.Guidance
import tz.co.digid.sdk.NidaDate
import tz.co.digid.sdk.Overall
import tz.co.digid.sdk.Sex
import tz.co.digid.sdk.StepKind
import tz.co.digid.sdk.Verdict
import java.util.Calendar

// The app's own words for the SDK's codes, the same as the drop-in flow's (res/values and
// res/values-sw follow the Flutter SDK's lib/src/ui/strings.dart).

/** The camera's title for a step. */
@StringRes
fun StepKind.label(): Int = when (this) {
    StepKind.SCAN_BACK -> R.string.step_card_back
    StepKind.SELFIE, StepKind.HOLD_CARD -> R.string.step_selfie
    else -> R.string.step_card
}

/** The flow's words for every guidance code (neutral: any ID card, no product names). */
@StringRes
fun Guidance.label(): Int? = when (this) {
    Guidance.NO_CARD -> R.string.guidance_no_card
    Guidance.MOVE_CLOSER -> R.string.guidance_move_closer
    Guidance.TOO_CLOSE -> R.string.guidance_too_close
    Guidance.BLURRY -> R.string.guidance_blurry
    Guidance.GLARE -> R.string.guidance_glare
    Guidance.TILTED -> R.string.guidance_tilted
    Guidance.HOLD_STILL -> R.string.guidance_hold_still
    Guidance.FLIP_TO_BACK -> R.string.guidance_flip_to_back
    Guidance.NO_FACE -> R.string.guidance_no_face
    Guidance.MULTIPLE_FACES -> R.string.guidance_multiple_faces
    Guidance.FACE_TOO_SMALL -> R.string.guidance_face_too_small
    Guidance.CARD_FACE_TOO_SMALL -> R.string.guidance_card_face_too_small
    Guidance.TURN_LEFT -> R.string.guidance_turn_left
    Guidance.TURN_RIGHT -> R.string.guidance_turn_right
    Guidance.BLINK -> R.string.guidance_blink
    Guidance.LOOK_STRAIGHT -> R.string.guidance_look_straight
    Guidance.LICENCE_REQUIRED -> R.string.guidance_licence_required
    Guidance.DONE -> R.string.guidance_done
    // A code from a newer engine: Digid.guidanceText has the engine's own words for it.
    Guidance.UNKNOWN -> null
}

/** Null for a check this version of the app does not know: show it by its code. */
@StringRes
fun CheckId.label(): Int? = when (this) {
    CheckId.REQUIRED_FIELDS -> R.string.check_required_fields
    CheckId.NIN_FORMAT -> R.string.check_nin_format
    CheckId.NIN_DOB_MATCH -> R.string.check_nin_dob_match
    CheckId.DOB_PLAUSIBLE -> R.string.check_dob_plausible
    CheckId.EXPIRY_VALID -> R.string.check_expiry_valid
    CheckId.CARD_TYPE_KNOWN -> R.string.check_card_type_known
    CheckId.NIN_CHECKSUM -> R.string.check_nin_checksum
    CheckId.LICENCE_NUMBER_FORMAT -> R.string.check_licence_number_format
    CheckId.ISSUE_DATE_VALID -> R.string.check_issue_date_valid
    CheckId.SIDES_MATCH -> R.string.check_sides_match
    CheckId.BACK_READ -> R.string.check_back_read
    CheckId.UNKNOWN -> null
}

/** The three outcomes shown to people, with their colour and filled Tabler icon. */
enum class Outcome(@param:StringRes val label: Int, @param:StringRes val count: Int, val tone: Tone, val dot: ImageVector, val shield: ImageVector) {
    PASSED(R.string.details_pass, R.string.details_count_pass, Tone.SUCCESS, Tabler.circleCheckFilled, Tabler.shieldCheck),
    TO_REVIEW(R.string.details_review, R.string.details_count_review, Tone.WARNING, Tabler.alertCircleFilled, Tabler.shieldExclamation),
    FAILED(R.string.details_fail, R.string.details_count_fail, Tone.DANGER, Tabler.circleXFilled, Tabler.shieldX),
}

fun Overall.outcome(): Outcome = when (this) {
    Overall.PASS -> Outcome.PASSED
    Overall.FAIL -> Outcome.FAILED
    Overall.REVIEW, Overall.UNKNOWN -> Outcome.TO_REVIEW
}

/** Null for checks with nothing to show (not applicable). */
fun CheckStatus.outcome(): Outcome? = when (this) {
    CheckStatus.PASS -> Outcome.PASSED
    CheckStatus.FAIL -> Outcome.FAILED
    CheckStatus.WARN, CheckStatus.UNVERIFIABLE, CheckStatus.UNKNOWN -> Outcome.TO_REVIEW
    CheckStatus.NOT_APPLICABLE -> null
}

fun Verdict.outcome(): Outcome = when (this) {
    Verdict.MATCH -> Outcome.PASSED
    Verdict.NO_MATCH -> Outcome.FAILED
    Verdict.UNCERTAIN, Verdict.UNKNOWN -> Outcome.TO_REVIEW
}

@StringRes
fun Verdict.label(): Int = when (this) {
    Verdict.MATCH -> R.string.verdict_match
    Verdict.NO_MATCH -> R.string.verdict_no_match
    Verdict.UNCERTAIN, Verdict.UNKNOWN -> R.string.verdict_uncertain
}

@StringRes
fun Sex.label(): Int? = when (this) {
    Sex.MALE -> R.string.sex_male
    Sex.FEMALE -> R.string.sex_female
    Sex.UNKNOWN -> null
}

@StringRes
fun CardType.label(): Int = when (this) {
    CardType.CITIZEN -> R.string.card_type_citizen
    CardType.RESIDENT -> R.string.card_type_resident
    CardType.REFUGEE -> R.string.card_type_refugee
    CardType.UNKNOWN -> R.string.card_type_unknown
}

/** The NIN as printed on the card: 8-5-5-2 digits. */
fun formatNin(nin: String): String =
    if (nin.length == 20 && nin.all(Char::isDigit)) {
        "${nin.substring(0, 8)}-${nin.substring(8, 13)}-${nin.substring(13, 18)}-${nin.substring(18)}"
    } else {
        nin
    }

/** Dates as day/month/year, the order used in Tanzania. */
fun NidaDate.display(): String = "%02d/%02d/%04d".format(day, month, year)

/** Whether the date is before today (java.util, as java.time needs API 26 or desugaring). */
fun NidaDate.isPast(now: Calendar = Calendar.getInstance()): Boolean {
    val today = now.get(Calendar.YEAR) * 10_000 + (now.get(Calendar.MONTH) + 1) * 100 + now.get(Calendar.DAY_OF_MONTH)
    return year * 10_000 + month * 100 + day < today
}
