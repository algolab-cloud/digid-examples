import Digid
import SwiftUI

/// The end of the flow, in its minimal layout: the status shield, the headline and one plain
/// sentence, tags, what was read in a white card, what was checked behind "See details", then
/// one pill and a "›" link. Outcomes only: the face match is shown as its verdict.
///
/// This screen is for the person holding the phone. Your backend makes the decision, from
/// `session.finishJSON()` (see `KycModel.finishAttempt`).
struct ResultScreen: View {
    @Environment(\.strings) private var s
    let result: KycResult
    let onDone: () -> Void
    let onTryAgain: () -> Void

    var body: some View {
        let noCard = result.card == nil && result.drivingLicence == nil && result.cardBack == nil && result.face == nil
        let licence = result.drivingLicence
        let outcome: Outcome = noCard ? .failed : result.overall.outcome
        // Try again after a failure or when no card was found; otherwise Done.
        let retry = noCard || result.overall == .fail

        MinimalPage(onBack: onDone) {
            VStack(spacing: 0) {
                StatusShield(outcome: outcome)
                Spacer().frame(height: FlowSpace.lg)
                MinimalTitle(text: s(headline(noCard: noCard).heading), size: 22).fadeSlideIn(delay: 60)
                Spacer().frame(height: FlowSpace.sm)
                MinimalLead(text: s(headline(noCard: noCard).message)).fadeSlideIn(delay: 100)
                let tags = self.tags(noCard: noCard)
                if !tags.isEmpty {
                    Spacer().frame(height: FlowSpace.lg)
                    // At most four short tags: two per line.
                    VStack(spacing: FlowSpace.sm) {
                        ForEach(Array(stride(from: 0, to: tags.count, by: 2)), id: \.self) { i in
                            HStack(spacing: FlowSpace.sm) {
                                ForEach(tags[i..<min(i + 2, tags.count)], id: \.0) { key, tone in Tag(label: s(key), tone: tone) }
                            }
                        }
                    }
                }
            }

            if !noCard {
                Spacer().frame(height: FlowSpace.xl)
                DetailCard(rows: readRows()).fadeSlideIn(delay: 160)

                if let back = result.cardBack {
                    Spacer().frame(height: FlowSpace.xl)
                    VStack(alignment: .leading, spacing: FlowSpace.sm) {
                        Text(s("result_group_back"))
                            .font(FlowType.labelLarge)
                            .foregroundColor(FlowColors.text)
                            .padding(.leading, FlowSpace.xs)
                        DetailCard(rows: backRows(back, licence: licence != nil))
                    }
                    .fadeSlideIn(delay: 200)
                }

                let items = checkItems()
                if !items.isEmpty {
                    Spacer().frame(height: FlowSpace.lg)
                    ChecksCard(items: items).fadeSlideIn(delay: 220)
                }
            }

            Spacer().frame(height: FlowSpace.xl)
            if retry {
                PrimaryButton(title: s("common_try_again"), action: onTryAgain)
                ChevronLink(title: s("common_done"), action: onDone).padding(.top, FlowSpace.xs)
            } else {
                PrimaryButton(title: s("common_done"), action: onDone)
                ChevronLink(title: s("common_take_again"), action: onTryAgain).padding(.top, FlowSpace.xs)
            }
        }
        .overlay(result.evaluation ? EvaluationWatermark() : nil)
    }

    private func headline(noCard: Bool) -> (heading: String, message: String) {
        if noCard { return ("result_no_card_heading", "result_no_card_message") }
        switch result.overall {
        case .pass where result.face != nil: return ("result_face_matched_heading", "result_face_matched_message")
        case .pass where result.drivingLicence != nil: return ("result_licence_read_heading", "result_licence_read_message")
        case .pass: return ("result_card_read_heading", "result_card_read_message")
        case .fail: return ("result_fail_heading", "result_fail_message")
        default: return ("result_review_heading", "result_review_message")
        }
    }

    /// The tags under the message.
    private func tags(noCard: Bool) -> [(String, Tone?)] {
        let expiry = result.drivingLicence?.expiryDate?.value ?? result.card?.expiryDate?.value
        let expired = expiry?.isPast == true
            || result.checks.contains { $0.id == .expiryValid && ($0.status == .fail || $0.status == .warn) }
        // The flow asked for the back (a back_read check) but none was read: it timed out.
        let backSkipped = result.cardBack == nil && result.checks.contains { $0.id == .backRead && $0.status != .pass }
        var tags: [(String, Tone?)] = []
        if !noCard && result.drivingLicence != nil { tags.append(("result_tag_driving_licence", nil)) }
        if !noCard && expired { tags.append(("result_tag_expired", .warning)) }
        if !noCard && backSkipped { tags.append(("result_tag_back_skipped", .warning)) }
        if result.evaluation { tags.append(("result_tag_evaluation", .warning)) }
        return tags
    }

    /// What was read from the front, and the selfie's verdict.
    private func readRows() -> [DetailRow] {
        var rows = result.drivingLicence.map(licenceRows) ?? nidaRows(result.card)
        if let face = result.face { rows.append(DetailRow(label: s("field_selfie"), value: s(face.verdict.labelKey))) }
        return rows
    }

    private func expires(_ date: NidaDate?) -> String? {
        guard let date else { return nil }
        return date.isPast ? "\(date.display) · \(s("field_expired"))" : date.display
    }

    /// The rows for a NIDA card front, in the flow's order.
    private func nidaRows(_ card: NidaFront?) -> [DetailRow] {
        let name = [card?.givenNames?.value, card?.surname?.value].compactMap { $0 }.joined(separator: " ")
        return [
            DetailRow(label: s("field_name"), value: name.isEmpty ? nil : name),
            DetailRow(label: s("field_nin"), value: card?.nin.map { formatNin($0.value) }),
            DetailRow(label: s("field_born"), value: card?.dateOfBirth?.value.display),
            DetailRow(label: s("field_sex"), value: card?.sex?.value.labelKey.map { s($0) }),
            DetailRow(label: s("field_card_type"), value: card?.cardType.map { s($0.labelKey) }),
            DetailRow(label: s("field_expires"), value: expires(card?.expiryDate?.value)),
        ]
    }

    /// The driving licence's fields, in the order the card numbers them.
    private func licenceRows(_ dl: DrivingLicence) -> [DetailRow] {
        let name = [dl.givenNames?.value, dl.surname?.value].compactMap { $0 }.joined(separator: " ")
        let categories = dl.categories?.value ?? []
        return [
            DetailRow(label: s("field_name"), value: name.isEmpty ? nil : name),
            DetailRow(label: s("field_licence_number"), value: dl.licenceNumber?.value),
            DetailRow(label: s("field_reference_number"), value: dl.referenceNumber?.value),
            DetailRow(label: s("field_date_of_birth"), value: dl.dateOfBirth?.value.display),
            DetailRow(label: s("field_issued"), value: dl.issueDate?.value.display),
            DetailRow(label: s("field_expires"), value: expires(dl.expiryDate?.value)),
            DetailRow(label: s("field_categories"), value: categories.isEmpty ? nil : categories.joined(separator: ", ")),
            DetailRow(label: s("field_issuing_authority"), value: dl.issuingAuthority?.value),
            DetailRow(label: s("field_residence"), value: dl.residence?.value),
        ]
    }

    /// The back of the card, briefly: the category table (licence) or NIN (NIDA), restriction
    /// codes, the barcode.
    private func backRows(_ b: CardBack, licence: Bool) -> [DetailRow] {
        var rows: [DetailRow] = []
        if licence {
            for c in b.categories {
                let dates = [c.issueDate?.display, c.expiryDate?.display].compactMap { $0 }.joined(separator: " – ")
                rows.append(DetailRow(label: s("field_category", c.code), value: dates.isEmpty ? nil : dates))
            }
            if b.categories.isEmpty { rows.append(DetailRow(label: s("field_categories"), value: nil)) }
            var codes: [String] = []
            for code in b.restrictions + b.categories.flatMap(\.restrictions) where !codes.contains(code) { codes.append(code) }
            rows.append(DetailRow(label: s("field_restriction_codes"), value: codes.isEmpty ? nil : codes.joined(separator: ", ")))
            if let number = b.licenceNumber { rows.append(DetailRow(label: s("field_licence_number"), value: number.value)) }
        } else {
            rows.append(DetailRow(label: s("field_nin"), value: b.nin?.value))
        }
        var formats: [String] = []
        for f in b.barcodes.map(\.format) where !formats.contains(f) { formats.append(f) }
        rows.append(DetailRow(
            label: s("field_barcode"),
            value: formats.isEmpty ? s("field_barcode_none") : s("field_barcode_found", formats.joined(separator: ", "))
        ))
        return rows
    }

    /// What was checked: every engine check that applies (pass → Passed, fail → Failed, warn or
    /// unverifiable → To review), the selfie match and liveness. Outcomes only.
    private func checkItems() -> [CheckItem] {
        var items: [CheckItem] = result.checks.compactMap { c in
            guard let outcome = c.status.outcome else { return nil }
            return CheckItem(label: c.id.labelKey.map { s($0) } ?? c.id.rawValue, outcome: outcome)
        }
        if let face = result.face { items.append(CheckItem(label: s("check_face"), outcome: face.verdict.outcome)) }
        if let liveness = result.liveness { items.append(CheckItem(label: s("check_liveness"), outcome: liveness.passed ? .passed : .failed)) }
        return items
    }
}

/// The diagonal "EVALUATION" watermark over an unlicensed result. Required in evaluation mode.
private struct EvaluationWatermark: View {
    var body: some View {
        Text("EVALUATION")
            .font(.system(size: 56, weight: .light))
            .kerning(4)
            .foregroundColor(FlowColors.text.opacity(0.05))
            .lineLimit(1)
            .fixedSize()
            .rotationEffect(.degrees(-34))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
