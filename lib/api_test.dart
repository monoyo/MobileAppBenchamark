import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'services/api_service.dart';

class ApiTestPage extends StatefulWidget {
  const ApiTestPage({super.key});

  @override
  State<ApiTestPage> createState() => _ApiTestPageState();
}

class _ApiTestPageState extends State<ApiTestPage> {
  final ApiService _api = ApiService();
  late int start;

  @override
  void initState() {
    super.initState();
    _start();
  }

  void _start() async {
    start = DateTime.now().millisecondsSinceEpoch;
    try {
      final posts = await _api.fetchPosts();
      final elapsed = DateTime.now().millisecondsSinceEpoch - start;
      final res = TestResult('API Test', elapsed, 'Fetched ${posts.length} posts', true);
      if (mounted) Navigator.pop(context, res);
    } catch (e) {
      if (mounted) Navigator.pop(context, TestResult('API Test', -1, e.toString(), false));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: const Center(child: Text('Running API test...')));
  }
}
