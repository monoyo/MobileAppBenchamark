import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/Post.dart';

class ApiService {
  final _base = Uri.parse('https://jsonplaceholder.typicode.com/posts');

  Future<List<Post>> fetchPosts() async {
    final resp = await http.get(_base,
        headers: {'Accept': 'application/json'});
    if (resp.statusCode != 200) throw Exception('API error ${resp.statusCode}');
    final List<dynamic> data = json.decode(resp.body);
    return data.map((e) => Post.fromJson(e as Map<String, dynamic>)).toList();
  }
}
