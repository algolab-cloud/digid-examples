/// Sends the result document to your backend, which makes the decision.
///
/// [payload] is the digid.kyc/1 document as JSON text, the same on every digid SDK. It holds
/// personal data: never log it or keep it on the phone.
///
/// TODO(integrator): post it as it is, over your own authenticated connection, and check it on
/// your server (https://digid.co.tz/docs/backend-verification). Do not parse and re-serialise
/// it first: the server checks that the text is untouched. For example, with package:http:
///
/// ```dart
/// await http.post(
///   Uri.parse('https://api.example.co.tz/kyc'),
///   headers: {'content-type': 'application/json', 'authorization': 'Bearer $sessionToken'},
///   body: payload,
/// );
/// ```
Future<void> sendToBackend(String payload) async {}
