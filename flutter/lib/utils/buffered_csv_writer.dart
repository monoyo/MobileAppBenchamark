import 'dart:io';

class BufferedCsvWriter {
  final String filePath;
  final int bufferSize;
  final List<String> _buffer = [];
  bool _initialized = false;
  IOSink? _sink;

  BufferedCsvWriter(this.filePath, {this.bufferSize = 1000});

  Future<void> initialize({String header = 'iteration,executionTimeMs,details,intervalStartMs,intervalDurationMs,cumulativeTimeMs'}) async {
    if (_initialized) return;
    try {
      final file = File(filePath);
      final parent = file.parent;
      if (!await parent.exists()) {
        await parent.create(recursive: true);
      }
      // Use append mode for safety, but initially write header if new
      _sink = file.openWrite(mode: FileMode.append);
      
      if (await file.length() == 0) {
        _sink?.writeln(header);
      }
      _initialized = true;
    } catch (e) {
      print('Failed to init CSV writer: $e');
    }
  }

  void write(
    int iteration,
    int executionTimeMs,
    String details, {
    int intervalStartMs = 0,
    int intervalDurationMs = 0,
    int cumulativeTimeMs = 0,
  }) {
    if (!_initialized) return;

    final safeDetails = _csvSafe(details);
    final line = '$iteration,$executionTimeMs,$safeDetails,$intervalStartMs,$intervalDurationMs,$cumulativeTimeMs';
    _buffer.add(line);

    if (_buffer.length >= bufferSize) {
      flush();
    }
  }

  Future<void> flush() async {
    if (_buffer.isEmpty || _sink == null) return;
    try {
      final chunk = _buffer.join('\n');
      _sink?.writeln(chunk);
      await _sink?.flush();
      _buffer.clear();
    } catch (e) {
      print('Flush error: $e');
    }
  }

  Future<void> close() async {
    await flush();
    await _sink?.close();
    _initialized = false;
  }

  String _csvSafe(String v) {
    if (v.contains(',') || v.contains('"') || v.contains('\n')) {
      return '"${v.replaceAll('"', '""')}"';
    }
    return v;
  }
}
