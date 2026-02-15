import 'dart:async';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart' hide Config;
import 'models/test_result.dart';
import 'consts/config.dart';
import 'utils/buffered_csv_writer.dart';

/// Image loading benchmark page.
/// Tests network performance with sequential image loading and scrolling.
/// Matches Java ImageLoadingTest: load image → scroll to next → load → scroll.
class ImageLoadingTest extends StatefulWidget {
  final int runId;
  final BufferedCsvWriter? writer;
  
  const ImageLoadingTest({
    super.key,
    required this.runId,
    this.writer,
  });

  @override
  State<ImageLoadingTest> createState() => _ImageLoadingTestState();
}

class _ImageLoadingTestState extends State<ImageLoadingTest> {
  static const List<String> _imageUrls = [
    "https://fastly.picsum.photos/id/861/300/200.jpg?hmac=SePZxFhkEpm4mmZIJke4z7ghH-2l0PsNAtEm_2vq2W4",
    "https://fastly.picsum.photos/id/687/300/200.jpg?hmac=4cY--ZSfxEMRzYtVmyvUBPrHqzAqJ3JmMSEmdYqdfMM",
    "https://fastly.picsum.photos/id/408/300/200.jpg?hmac=WLBoOapFRUAh4eGfCSPD4htVThRV8LKEnheDBbmOYvY",
    "https://fastly.picsum.photos/id/297/300/200.jpg?hmac=FHS6m7Ec_3-9rDv45kvf5XCQz0tWD5sY9yZY7GwSC6c",
    "https://fastly.picsum.photos/id/723/300/200.jpg?hmac=r-Bu4Me1tZJW3ncPjx4Pj2nhJ2sV0XQhDEeM1kH9EyY",
    "https://fastly.picsum.photos/id/507/300/200.jpg?hmac=H7vqiU7dtXTNLQraEHG25D7naP8nQy-uGlbyUCvE6Mo",
    "https://fastly.picsum.photos/id/163/300/200.jpg?hmac=fHGMH6DT42ra3SOzs6JtojmYZ7jECNcq5xn1Ap9OPNA",
    "https://fastly.picsum.photos/id/54/300/200.jpg?hmac=7Cm5bybfBDMHwUF7AvEbAKWA7l5WnE9MZvcZhPpULTc",
    "https://fastly.picsum.photos/id/992/300/200.jpg?hmac=w137wSlXMe7QugWkdz2qvxFlif1dwEWqNnv4qFIyWps",
    "https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20",
  ];

  static const int _scrollItemHeight = 600; // Match Java: 600px item height
  static const int _scrollDelayMs = 50; // Match Java: 50ms delay before scroll

  late final int _startMs;
  late final ScrollController _scrollController;

  int _loadedCount = 0;
  bool _testCompleted = false;
  // Track per-image start time
  final Map<int, int> _imageStartTimes = {};

  int get _itemCount => Config.sampleCount;

  /// Get image URL for given index (cycling through the 10 URLs)
  String _getUrl(int index) => _imageUrls[index % _imageUrls.length];

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    _startMs = DateTime.now().millisecondsSinceEpoch;
    // Clear cache to force fresh downloads
    DefaultCacheManager().emptyCache();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  /// Called when an image finishes loading (success or failure).
  /// Matches Java's handleLoadResult: increment counter, scroll to next.
  void _handleLoadResult(int position, bool success) {
    if (_testCompleted || !mounted) return;
    if (position != _loadedCount) return;

    _loadedCount++;
    
    // Write per-image row to CSV
    final now = DateTime.now().millisecondsSinceEpoch;
    final imageStart = _imageStartTimes[position] ?? now;
    final imageDuration = now - imageStart;
    
    if (widget.writer != null) {
      widget.writer!.write(
        _loadedCount,
        imageDuration,
        success ? 'Success' : 'Error',
        intervalStartMs: imageStart,
        intervalDurationMs: imageDuration,
        cumulativeTimeMs: now - _startMs,
      );
    }
    
    if (_loadedCount >= _itemCount) {
      _completeTest();
      return;
    }

    // Scroll to next item after short delay — matches Java:
    // mainHandler.postDelayed(() -> recyclerView.smoothScrollToPosition(...), 50)
    Future.delayed(const Duration(milliseconds: _scrollDelayMs), () {
      if (!mounted || _testCompleted || !_scrollController.hasClients) return;
      
      // Calculate scroll position for next item
      final targetOffset = _loadedCount * _scrollItemHeight.toDouble();
      final maxScroll = _scrollController.position.maxScrollExtent;
      
      _scrollController.animateTo(
        targetOffset.clamp(0.0, maxScroll),
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );

      // Trigger rebuild so next item starts loading
      setState(() {});
    });
  }

  void _completeTest() {
    if (_testCompleted || !mounted) return;
    _testCompleted = true;

    final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startMs;
    final result = TestResult(
      'Image Loading Test',
      elapsedMs,
      'Batch completed: $_loadedCount',
      true,
    );

    Navigator.pop(context, result);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListView.builder(
        controller: _scrollController,
        itemExtent: _scrollItemHeight.toDouble(),
        cacheExtent: 2000,
        itemCount: _itemCount,
        itemBuilder: (context, index) => _buildImageTile(index),
      ),
    );
  }

  Widget _buildImageTile(int index) {
    // Current item being loaded
    if (index == _loadedCount) {
      return _buildLoadingImage(index);
    }

    // Already loaded (above current)
    if (index < _loadedCount) {
      return Container(
        height: _scrollItemHeight.toDouble(),
        color: Colors.grey[300],
        child: CachedNetworkImage(
          imageUrl: _getUrl(index),
          fit: BoxFit.cover,
          errorWidget: (context, url, error) => _buildErrorWidget(),
        ),
      );
    }

    // Not yet reached (below current)
    return Container(
      height: _scrollItemHeight.toDouble(),
      color: Colors.grey[200],
    );
  }

  Widget _buildLoadingImage(int index) {
    final url = _getUrl(index);
    // Record when this image starts loading
    _imageStartTimes[index] = DateTime.now().millisecondsSinceEpoch;
    return SizedBox(
      height: _scrollItemHeight.toDouble(),
      child: CachedNetworkImage(
        imageUrl: url,
        // Force fresh download every time — unique key per index
        cacheKey: 'img_${widget.runId}_$index',
        useOldImageOnUrlChange: false,
        fit: BoxFit.cover,
        placeholder: (context, url) => Container(
          color: Colors.grey[300],
          child: const Center(child: CircularProgressIndicator()),
        ),
        errorWidget: (context, url, error) {
          // Schedule callback to avoid setState during build
          WidgetsBinding.instance.addPostFrameCallback((_) {
            _handleLoadResult(index, false);
          });
          return _buildErrorWidget();
        },
        imageBuilder: (context, imageProvider) {
          // Image loaded successfully — notify after build
          WidgetsBinding.instance.addPostFrameCallback((_) {
            _handleLoadResult(index, true);
          });
          return Image(image: imageProvider, fit: BoxFit.cover);
        },
      ),
    );
  }

  Widget _buildErrorWidget() {
    return Container(
      color: Colors.grey[300],
      child: const Center(
        child: Icon(Icons.error, color: Colors.red, size: 48),
      ),
    );
  }
}
