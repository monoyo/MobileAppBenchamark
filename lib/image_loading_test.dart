import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'package:cached_network_image/cached_network_image.dart';

class ImageLoadingTestPage extends StatefulWidget {
  const ImageLoadingTestPage({super.key});

  @override
  State<ImageLoadingTestPage> createState() => _ImageLoadingTestPageState();
}

class _ImageLoadingTestPageState extends State<ImageLoadingTestPage> {
  final ScrollController _scrollController = ScrollController();

  List<String> urls = [
    "https://fastly.picsum.photos/id/861/300/200.jpg?hmac=SePZxFhkEpm4mmZIJke4z7ghH-2l0PsNAtEm_2vq2W4",
    "https://fastly.picsum.photos/id/687/300/200.jpg?hmac=4cY--ZSfxEMRzYtVmyvUBPrHqzAqJ3JmMSEmdYqdfMM",
    "https://fastly.picsum.photos/id/408/300/200.jpg?hmac=WLBoOapFRUAh4eGfCSPD4htVThRV8LKEnheDBbmOYvY",
    "https://fastly.picsum.photos/id/297/300/200.jpg?hmac=FHS6m7Ec_3-9rDv45kvf5XCQz0tWD5sY9yZY7GwSC6c",
    "https://fastly.picsum.photos/id/723/300/200.jpg?hmac=r-Bu4Me1tZJW3ncPjx4Pj2nhJ2sV0XQhDEeM1kH9EyY",
    "https://fastly.picsum.photos/id/507/300/200.jpg?hmac=H7vqiU7dtXTNLQraEHG25D7naP8nQy-uGlbyUCvE6Mo",
    "https://fastly.picsum.photos/id/163/300/200.jpg?hmac=fHGMH6DT42ra3SOzs6JtojmYZ7jECNcq5xn1Ap9OPNA",
    "https://fastly.picsum.photos/id/54/300/200.jpg?hmac=7Cm5bybfBDMHwUF7AvEbAKWA7l5WnE9MZvcZhPpULTc",
    "https://fastly.picsum.photos/id/992/300/200.jpg?hmac=w137wSlXMe7QugWkdz2qvxFlif1dwEWqNnv4qFIyWps",
    "https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20"
  ];

  int loaded = 0;
  int failed = 0;
  late List<bool> _done;
  bool _completed = false;
  late int start;

  @override
  void initState() {
    super.initState();
    start = DateTime.now().millisecondsSinceEpoch;
    _done = List<bool>.filled(urls.length, false);
  }

  void _markDone(int index, {required bool success}) {
    if (_completed || !mounted) return;
    if (index < 0 || index >= urls.length) return;
    if (_done[index]) return; // licz tylko raz na obrazek
    _done[index] = true;
    if (success) {
      loaded++;
    } else {
      failed++;
    }

    if ((loaded + failed) >= urls.length) {
      if (_completed) return;
      _completed = true;
      final elapsed = DateTime.now().millisecondsSinceEpoch - start;
      final res = TestResult(
        'Image Loading Test',
        elapsed,
        'loaded=$loaded, failed=$failed',
        true,
      );
      Navigator.pop(context, res);
    } else {
      // przewiń do ostatnio przetworzonego indeksu
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        if (_scrollController.hasClients) {
          _scrollController.animateTo(
            index * 200.0,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListView.builder(
        controller: _scrollController,
        itemCount: urls.length,
        itemBuilder: (c, i) {
          return SizedBox(
            height: 200,
            child: CachedNetworkImage(
              imageUrl: urls[i],
              placeholder: (c, u) =>
                  const Center(child: CircularProgressIndicator()),
              errorWidget: (c, u, e) {
                // Zlicz błąd tylko raz dla danego indeksu
                WidgetsBinding.instance.addPostFrameCallback((_) => _markDone(i, success: false));
                return Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    const Icon(Icons.error, color: Colors.red),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        'Error loading image',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Colors.red),
                      ),
                    ),
                  ],
                );
              },
              imageBuilder: (ctx, imageProvider) {
                // Zlicz sukces tylko raz dla danego indeksu
                WidgetsBinding.instance
                    .addPostFrameCallback((_) => _markDone(i, success: true));
                return Image(image: imageProvider, fit: BoxFit.cover);
              },
            ),
          );
        },
      ),
    );
  }
}
