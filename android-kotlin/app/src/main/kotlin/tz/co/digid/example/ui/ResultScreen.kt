package tz.co.digid.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tz.co.digid.example.R
import tz.co.digid.example.ui.theme.FlowColors
import tz.co.digid.example.ui.theme.FlowSpace
import tz.co.digid.example.ui.theme.FlowType
import tz.co.digid.example.ui.theme.Tone
import tz.co.digid.sdk.CardBack
import tz.co.digid.sdk.CheckId
import tz.co.digid.sdk.CheckStatus
import tz.co.digid.sdk.DrivingLicence
import tz.co.digid.sdk.KycResult
import tz.co.digid.sdk.NidaDate
import tz.co.digid.sdk.NidaFront
import tz.co.digid.sdk.Overall

/**
 * The end of the flow, in its minimal layout (the Flutter SDK's result_page.dart): the status
 * shield, the headline and one plain sentence, tags, what was read in a white card, what was
 * checked behind "See details", then one pill and a "›" link. Outcomes only: the face match is
 * shown as its verdict only.
 *
 * This screen is for the person holding the phone. Your backend makes the decision, from
 * `session.finishJson()` (see KycViewModel.finishAttempt).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(result: KycResult, onDone: () -> Unit, onTryAgain: () -> Unit) {
    val noCard = result.card == null && result.drivingLicence == null && result.cardBack == null && result.face == null
    val licence = result.drivingLicence
    val outcome = if (noCard) Outcome.FAILED else result.overall.outcome()
    val (heading, message) = when {
        noCard -> R.string.result_no_card_heading to R.string.result_no_card_message
        result.overall == Overall.PASS && result.face != null -> R.string.result_face_matched_heading to R.string.result_face_matched_message
        result.overall == Overall.PASS && licence != null -> R.string.result_licence_read_heading to R.string.result_licence_read_message
        result.overall == Overall.PASS -> R.string.result_card_read_heading to R.string.result_card_read_message
        result.overall == Overall.FAIL -> R.string.result_fail_heading to R.string.result_fail_message
        else -> R.string.result_review_heading to R.string.result_review_message
    }

    // The tags under the message.
    val expired = (licence?.expiryDate?.value ?: result.card?.expiryDate?.value)?.isPast() == true ||
        result.checks.any { it.id == CheckId.EXPIRY_VALID && (it.status == CheckStatus.FAIL || it.status == CheckStatus.WARN) }
    // The flow asked for the back (a back_read check) but none was read: it timed out.
    val backSkipped = result.cardBack == null && result.checks.any { it.id == CheckId.BACK_READ && it.status != CheckStatus.PASS }
    val tags = buildList {
        if (!noCard && licence != null) add(R.string.result_tag_driving_licence to null)
        if (!noCard && expired) add(R.string.result_tag_expired to Tone.WARNING)
        if (!noCard && backSkipped) add(R.string.result_tag_back_skipped to Tone.WARNING)
        if (result.evaluation) add(R.string.result_tag_evaluation to Tone.WARNING)
    }

    // Try again after a failure or when no card was found; otherwise Done.
    val retry = noCard || result.overall == Overall.FAIL

    MinimalPage(onBack = onDone, overlay = if (result.evaluation) watermark else null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            StatusShield(outcome.tone, outcome.shield)
            Spacer(Modifier.height(FlowSpace.lg))
            FadeSlideIn(60) { MinimalTitle(stringResource(heading), size = 22) }
            Spacer(Modifier.height(FlowSpace.sm))
            FadeSlideIn(100) { MinimalLead(stringResource(message)) }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(FlowSpace.lg))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FlowSpace.sm, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(FlowSpace.sm)) {
                    for ((label, tone) in tags) Tag(stringResource(label), tone)
                }
            }
        }

        if (!noCard) {
            val rows = buildList {
                addAll(if (licence != null) licenceRows(licence) else nidaRows(result.card))
                result.face?.let { add(DetailRow(stringResource(R.string.field_selfie), stringResource(it.verdict.label()))) }
            }
            Spacer(Modifier.height(FlowSpace.xl))
            FadeSlideIn(160) { DetailCard(rows) }

            result.cardBack?.let { back ->
                Spacer(Modifier.height(FlowSpace.xl))
                FadeSlideIn(200) {
                    Column {
                        Text(stringResource(R.string.result_group_back), style = FlowType.labelLarge, modifier = Modifier.padding(start = FlowSpace.xs, bottom = FlowSpace.sm))
                        DetailCard(backRows(back, licence = licence != null))
                    }
                }
            }

            val items = checkItems(result)
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(FlowSpace.lg))
                FadeSlideIn(220) { ChecksCard(items, collapsed = true) }
            }
        }

        Spacer(Modifier.height(FlowSpace.xl))
        if (retry) {
            PrimaryButton(stringResource(R.string.common_try_again), onTryAgain)
            Links { ChevronLink(stringResource(R.string.common_done), onDone) }
        } else {
            PrimaryButton(stringResource(R.string.common_done), onDone)
            Links { ChevronLink(stringResource(R.string.common_take_again), onTryAgain) }
        }
    }
}

@Composable
private fun Links(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxWidth().padding(top = FlowSpace.xs), contentAlignment = Alignment.Center) { content() }

/** The diagonal "EVALUATION" watermark over an unlicensed result. Required in evaluation mode. */
private val watermark: @Composable () -> Unit = { EvaluationWatermark() }

@Composable
private fun EvaluationWatermark() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "EVALUATION",
            style = FlowType.displaySmall.copy(fontSize = 56.sp, letterSpacing = 4.sp, color = FlowColors.text.copy(alpha = .05f)),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.rotate(-34f).padding(horizontal = 8.dp),
        )
    }
}

private fun NidaDate?.shown(): String? = this?.display()

@Composable
private fun expires(date: NidaDate?): String? = date?.let {
    if (it.isPast()) "${it.display()} · ${stringResource(R.string.field_expired)}" else it.display()
}

/** The rows for a NIDA card front, in the flow's order. */
@Composable
private fun nidaRows(card: NidaFront?): List<DetailRow> {
    val name = listOfNotNull(card?.givenNames?.value, card?.surname?.value).joinToString(" ")
    return listOf(
        DetailRow(stringResource(R.string.field_name), name.ifEmpty { null }),
        DetailRow(stringResource(R.string.field_nin), card?.nin?.value?.let(::formatNin)),
        DetailRow(stringResource(R.string.field_born), card?.dateOfBirth?.value.shown()),
        DetailRow(stringResource(R.string.field_sex), card?.sex?.value?.label()?.let { stringResource(it) }),
        DetailRow(stringResource(R.string.field_card_type), card?.cardType?.let { stringResource(it.label()) }),
        DetailRow(stringResource(R.string.field_expires), expires(card?.expiryDate?.value)),
    )
}

/** The driving licence's fields, in the order the card numbers them. */
@Composable
private fun licenceRows(dl: DrivingLicence): List<DetailRow> {
    val name = listOfNotNull(dl.givenNames?.value, dl.surname?.value).joinToString(" ")
    return listOf(
        DetailRow(stringResource(R.string.field_name), name.ifEmpty { null }),
        DetailRow(stringResource(R.string.field_licence_number), dl.licenceNumber?.value),
        DetailRow(stringResource(R.string.field_reference_number), dl.referenceNumber?.value),
        DetailRow(stringResource(R.string.field_date_of_birth), dl.dateOfBirth?.value.shown()),
        DetailRow(stringResource(R.string.field_issued), dl.issueDate?.value.shown()),
        DetailRow(stringResource(R.string.field_expires), expires(dl.expiryDate?.value)),
        DetailRow(stringResource(R.string.field_categories), dl.categories?.value?.takeIf { it.isNotEmpty() }?.joinToString(", ")),
        DetailRow(stringResource(R.string.field_issuing_authority), dl.issuingAuthority?.value),
        DetailRow(stringResource(R.string.field_residence), dl.residence?.value),
    )
}

/** The back of the card, briefly: the category table (licence) or NIN (NIDA), restriction codes, the barcode. */
@Composable
private fun backRows(b: CardBack, licence: Boolean): List<DetailRow> = buildList {
    if (licence) {
        for (c in b.categories) {
            val dates = listOfNotNull(c.issueDate.shown(), c.expiryDate.shown()).joinToString(" – ")
            add(DetailRow(stringResource(R.string.field_category, c.code), dates.ifEmpty { null }))
        }
        if (b.categories.isEmpty()) add(DetailRow(stringResource(R.string.field_categories), null))
        val codes = (b.restrictions + b.categories.flatMap { it.restrictions }).distinct()
        add(DetailRow(stringResource(R.string.field_restriction_codes), codes.takeIf { it.isNotEmpty() }?.joinToString(", ")))
        b.licenceNumber?.let { add(DetailRow(stringResource(R.string.field_licence_number), it.value)) }
    } else {
        add(DetailRow(stringResource(R.string.field_nin), b.nin?.value))
    }
    val formats = b.barcodes.map { it.format }.distinct()
    add(
        DetailRow(
            stringResource(R.string.field_barcode),
            if (formats.isEmpty()) stringResource(R.string.field_barcode_none) else stringResource(R.string.field_barcode_found, formats.joinToString(", ")),
        ),
    )
}

/**
 * What was checked: every engine check that applies (pass → Passed, fail → Failed, warn or
 * unverifiable → To review), the selfie match and liveness. Outcomes only.
 */
@Composable
private fun checkItems(r: KycResult): List<CheckItem> = buildList {
    for (c in r.checks) {
        val outcome = c.status.outcome() ?: continue
        add(CheckItem(c.id.label()?.let { stringResource(it) } ?: c.id.wire, outcome))
    }
    r.face?.let { add(CheckItem(stringResource(R.string.check_face), it.verdict.outcome())) }
    r.liveness?.let { add(CheckItem(stringResource(R.string.check_liveness), if (it.passed) Outcome.PASSED else Outcome.FAILED)) }
}
