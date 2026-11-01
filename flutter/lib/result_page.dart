import 'package:digid/digid.dart';
import 'package:flutter/material.dart';

import 'strings.dart';

/// What the flow returned to the app. This screen is for the person holding the phone: the
/// decision is made on your backend, from the result document.
class ResultPage extends StatelessWidget {
  const ResultPage({super.key, required this.result});
  final DigidFlowResult result;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final kyc = result.kyc;
    return ValueListenableBuilder<Lang>(
      valueListenable: appLang,
      builder: (context, _, _) {
        final (icon, color, verdict) = switch (kyc?.overall) {
          Overall.pass => (Icons.verified_outlined, const Color(0xFF16A34A), tr('Verified', 'Imethibitishwa')),
          Overall.review => (Icons.help_outline, const Color(0xFFD97706), tr('Needs review', 'Inahitaji ukaguzi')),
          _ => (Icons.cancel_outlined, const Color(0xFFDC2626), tr('Not verified', 'Haijathibitishwa')),
        };
        return Scaffold(
          appBar: AppBar(title: Text(tr('Result', 'Matokeo')), backgroundColor: Colors.white),
          body: SafeArea(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
              children: [
                Icon(icon, size: 56, color: color),
                const SizedBox(height: 16),
                Text(verdict, style: t.headlineMedium?.copyWith(fontWeight: FontWeight.w300)),
                if (result.evaluation) ...[
                  const SizedBox(height: 8),
                  Text(
                    tr(
                      'Evaluation: no valid licence, so never better than Review.',
                      'Majaribio: hakuna leseni halali, kwa hiyo hayazidi "Inahitaji ukaguzi".',
                    ),
                    style: t.bodySmall?.copyWith(color: const Color(0xFF8A5A00)),
                  ),
                ],
                const SizedBox(height: 24),
                _Row(tr('Document', 'Hati'), _document(kyc)),
                if (kyc?.face != null) _Row(tr('Selfie', 'Selfie'), _face(kyc!.face!.verdict)),
                if (kyc?.liveness != null)
                  _Row(tr('Live person', 'Mtu halisi'), kyc!.liveness!.passed ? tr('Passed', 'Imepita') : tr('Failed', 'Imeshindwa')),
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(color: const Color(0xFFF1F2FF), borderRadius: BorderRadius.circular(16)),
                  child: Text(
                    result.payload == null
                        ? tr('The result document could not be built. Try again.', 'Hati ya matokeo haikuweza kuandaliwa. Jaribu tena.')
                        : tr(
                            'Next, the app sends the result document to its backend, which makes the decision.',
                            'Kisha programu inatuma hati ya matokeo kwa seva yake, ambayo ndiyo inaamua.',
                          ),
                    style: t.bodyMedium,
                  ),
                ),
                const SizedBox(height: 32),
                FilledButton(onPressed: () => Navigator.of(context).pop(), child: Text(tr('Done', 'Maliza'))),
              ],
            ),
          ),
        );
      },
    );
  }

  String _document(KycResult? kyc) {
    if (kyc?.drivingLicence != null) return tr('Driving licence', 'Leseni ya udereva');
    if (kyc?.card != null) return tr('NIDA ID card', 'Kitambulisho cha NIDA');
    return tr('No card read', 'Hakuna kitambulisho kilichosomwa');
  }

  String _face(Verdict v) => switch (v) {
    Verdict.match => tr('Matches the ID', 'Inalingana na kitambulisho'),
    Verdict.uncertain => tr('To review', 'Ikaguliwe'),
    Verdict.noMatch => tr('Does not match', 'Hailingani'),
  };
}

class _Row extends StatelessWidget {
  const _Row(this.label, this.value);
  final String label, value;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Row(
        children: [
          Expanded(
            child: Text(label, style: t.bodyMedium?.copyWith(color: Colors.black54)),
          ),
          Text(value, style: t.bodyMedium?.copyWith(fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}
