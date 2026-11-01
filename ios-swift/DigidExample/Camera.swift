import AVFoundation
import Digid
import ImageIO
import SwiftUI
import UIKit

/// The camera: one `AVCaptureSession` with a BGRA video output (frames for the engine) and a
/// photo output (one full-resolution photo of the front of the card).
///
/// - The back camera runs at 1920 x 1080: card text needs the pixels (720p is too small). Its
///   format is picked so it can also take full-resolution photos.
/// - The front camera is used for the selfie.
/// - Frames arrive upright (the connection is set to portrait), so they are pushed with a
///   rotation of 0, and never mirrored: only the preview is drawn mirrored on the front camera.
final class CameraController: NSObject, @unchecked Sendable {
    enum CameraError: Error {
        case noCamera, cannotAddInput, cannotAddOutput
    }

    let session = AVCaptureSession()

    /// Starting, switching and stopping the camera block: they run on this queue.
    private let configQueue = DispatchQueue(label: "digid-example.camera")
    private let videoOutput = AVCaptureVideoDataOutput()
    private let photoOutput = AVCapturePhotoOutput()
    private var device: AVCaptureDevice?
    private var outputsAdded = false
    /// Photo delegates are not retained by AVFoundation: keep each until its photo arrives.
    private var photoDelegates: [Int64: PhotoDelegate] = [:]

    /// Opens the back (`front` false) or front camera and sends its frames to `delegate` on
    /// `queue`. `completion` runs on the main queue with whether the camera has a torch.
    func start(front: Bool, delegate: AVCaptureVideoDataOutputSampleBufferDelegate, queue: DispatchQueue,
               completion: @escaping (Result<Bool, Error>) -> Void) {
        configQueue.async { [self] in
            do {
                try configure(front: front)
                videoOutput.setSampleBufferDelegate(delegate, queue: queue)
                if !session.isRunning { session.startRunning() }
                let torch = !front && (device?.hasTorch ?? false)
                log.info("camera started (\(front ? "front" : "back", privacy: .public))")
                DispatchQueue.main.async { completion(.success(torch)) }
            } catch {
                log.error("camera failed to start: \(String(describing: error), privacy: .public)")
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }

    /// Stops the frames and the camera.
    func stop() {
        configQueue.async { [self] in
            videoOutput.setSampleBufferDelegate(nil, queue: nil)
            setTorchNow(false)
            if session.isRunning { session.stopRunning() }
        }
    }

    func setTorch(_ on: Bool) {
        configQueue.async { [self] in setTorchNow(on) }
    }

    private func setTorchNow(_ on: Bool) {
        guard let device, device.hasTorch, (try? device.lockForConfiguration()) != nil else { return }
        device.torchMode = on ? .on : .off
        device.unlockForConfiguration()
    }

    private func configure(front: Bool) throws {
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        for input in session.inputs { session.removeInput(input) }
        setTorchNow(false)
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: front ? .front : .back) else {
            throw CameraError.noCamera
        }
        // 1080p when the camera has it. The back camera then gets a 1080p format that also takes
        // full-resolution photos (below); the selfie needs no photo.
        session.sessionPreset = session.canSetSessionPreset(.hd1920x1080) ? .hd1920x1080 : .high
        let input = try AVCaptureDeviceInput(device: camera)
        guard session.canAddInput(input) else { throw CameraError.cannotAddInput }
        session.addInput(input)
        device = camera

        if !outputsAdded {
            videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
            // The engine processes one frame at a time: frames that arrive meanwhile are dropped.
            videoOutput.alwaysDiscardsLateVideoFrames = true
            guard session.canAddOutput(videoOutput), session.canAddOutput(photoOutput) else { throw CameraError.cannotAddOutput }
            session.addOutput(videoOutput)
            session.addOutput(photoOutput)
            outputsAdded = true
        }

        try camera.lockForConfiguration()
        if !front, let format = Self.cardFormat(camera) {
            // Setting the format switches the session to the device's own settings (.inputPriority).
            camera.activeFormat = format
            camera.activeVideoMinFrameDuration = CMTime(value: 1, timescale: 30)
        }
        if camera.isFocusModeSupported(.continuousAutoFocus) { camera.focusMode = .continuousAutoFocus }
        // Cards are held close: favour near focus where the camera supports it.
        if camera.isAutoFocusRangeRestrictionSupported { camera.autoFocusRangeRestriction = front ? .none : .near }
        if camera.isExposureModeSupported(.continuousAutoExposure) { camera.exposureMode = .continuousAutoExposure }
        camera.unlockForConfiguration()

        for connection in [videoOutput.connection(with: .video), photoOutput.connection(with: .video)].compactMap({ $0 }) {
            Self.portrait(connection)
            if connection.isVideoMirroringSupported {
                // Frames stay unmirrored, even from the front camera: a mirrored selfie would not
                // match the card's portrait as well.
                connection.automaticallyAdjustsVideoMirroring = false
                connection.isVideoMirrored = false
            }
        }

        // Full-resolution photos of the card.
        if #available(iOS 16.0, *) {
            if let largest = camera.activeFormat.supportedMaxPhotoDimensions.max(by: { $0.width * $0.height < $1.width * $1.height }) {
                photoOutput.maxPhotoDimensions = largest
            }
        } else {
            photoOutput.isHighResolutionCaptureEnabled = true
        }
    }

    /// The back camera's 1920 x 1080 format with the largest photos.
    private static func cardFormat(_ device: AVCaptureDevice) -> AVCaptureDevice.Format? {
        device.formats
            .filter { format in
                let d = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
                return d.width == 1920 && d.height == 1080
                    && format.videoSupportedFrameRateRanges.contains { $0.maxFrameRate >= 30 }
            }
            .max { photoArea($0) < photoArea($1) }
    }

    private static func photoArea(_ format: AVCaptureDevice.Format) -> Int32 {
        if #available(iOS 16.0, *) {
            return format.supportedMaxPhotoDimensions.map { $0.width * $0.height }.max() ?? 0
        }
        let d = format.highResolutionStillImageDimensions
        return d.width * d.height
    }

    /// Upright frames and photos for a portrait screen.
    private static func portrait(_ connection: AVCaptureConnection) {
        if #available(iOS 17.0, *) {
            if connection.isVideoRotationAngleSupported(90) { connection.videoRotationAngle = 90 }
        } else if connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
    }

    // MARK: - The photo of the card

    /// Takes one full-resolution photo and hands it over as BGRA pixels, with the rotation that
    /// makes it upright; nil when the photo failed. `completion` runs on an AVFoundation queue.
    func capturePhoto(_ completion: @escaping (NidaImage?) -> Void) {
        configQueue.async { [self] in
            guard session.isRunning else { return completion(nil) }
            let bgra = kCVPixelFormatType_32BGRA
            // Uncompressed BGRA when the camera offers it (no JPEG round trip), otherwise the
            // default format, decoded below.
            let settings = photoOutput.availablePhotoPixelFormatTypes.contains(bgra)
                ? AVCapturePhotoSettings(format: [kCVPixelBufferPixelFormatTypeKey as String: bgra])
                : AVCapturePhotoSettings()
            if #available(iOS 16.0, *) {
                settings.maxPhotoDimensions = photoOutput.maxPhotoDimensions
            } else {
                settings.isHighResolutionPhotoEnabled = true
            }
            let id = settings.uniqueID
            let delegate = PhotoDelegate { [weak self] image in
                self?.configQueue.async { self?.photoDelegates[id] = nil }
                completion(image)
            }
            photoDelegates[id] = delegate
            photoOutput.capturePhoto(with: settings, delegate: delegate)
        }
    }

    private final class PhotoDelegate: NSObject, AVCapturePhotoCaptureDelegate {
        let completion: (NidaImage?) -> Void
        init(_ completion: @escaping (NidaImage?) -> Void) { self.completion = completion }

        func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
            if let error {
                log.error("photo failed: \(String(describing: error), privacy: .public)")
                return completion(nil)
            }
            // The photo's pixels keep the sensor's orientation; its metadata says how to turn it upright.
            let orientation = (photo.metadata[kCGImagePropertyOrientation as String] as? NSNumber)?.uint32Value ?? 1
            let rotation = Self.clockwiseDegrees(exifOrientation: orientation)
            if let pixels = photo.pixelBuffer {
                completion(NidaImage.bgra(pixels, rotationDeg: rotation))
            } else if let data = photo.fileDataRepresentation() {
                completion(NidaImage.decoded(data, rotationDeg: rotation))
            } else {
                completion(nil)
            }
        }

        /// The clockwise turn that makes an image with this EXIF orientation upright.
        private static func clockwiseDegrees(exifOrientation: UInt32) -> Int {
            switch CGImagePropertyOrientation(rawValue: exifOrientation) {
            case .right, .rightMirrored: return 90
            case .down, .downMirrored: return 180
            case .left, .leftMirrored: return 270
            default: return 0
            }
        }
    }
}

extension NidaImage {
    /// A copy of a BGRA pixel buffer (its row padding kept as the row stride).
    static func bgra(_ buffer: CVPixelBuffer, rotationDeg: Int = 0) -> NidaImage? {
        guard CVPixelBufferGetPixelFormatType(buffer) == kCVPixelFormatType_32BGRA,
              CVPixelBufferLockBaseAddress(buffer, .readOnly) == kCVReturnSuccess
        else { return nil }
        defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(buffer) else { return nil }
        let stride = CVPixelBufferGetBytesPerRow(buffer)
        let height = CVPixelBufferGetHeight(buffer)
        return NidaImage(data: Data(bytes: base, count: stride * height), width: CVPixelBufferGetWidth(buffer),
                         height: height, format: .bgra8, rowStride: stride, rotationDeg: rotationDeg)
    }

    /// A JPEG or HEIC photo decoded to BGRA pixels, as stored (the rotation says how to turn it upright).
    static func decoded(_ data: Data, rotationDeg: Int) -> NidaImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
        else { return nil }
        let width = image.width, height = image.height, stride = width * 4
        var pixels = Data(count: stride * height)
        let drawn = pixels.withUnsafeMutableBytes { raw -> Bool in
            guard let context = CGContext(
                data: raw.baseAddress, width: width, height: height, bitsPerComponent: 8, bytesPerRow: stride,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return nil }
        return NidaImage(data: pixels, width: width, height: height, format: .bgra8, rowStride: stride, rotationDeg: rotationDeg)
    }
}

/// The camera picture, filling the screen (cropped, not letterboxed).
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}
