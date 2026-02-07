/// Represents a user entity with demographic information.
/// Used in RAM and data processing benchmarks.
class User {
  final String name;
  final String surname;
  final int age;
  final bool active;

  const User({
    required this.name,
    required this.surname,
    required this.age,
    required this.active,
  });

  /// Creates a User instance from JSON data.
  /// Used when deserializing from assets/API responses.
  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      name: json['name'] as String? ?? '',
      surname: json['surname'] as String? ?? '',
      age: json['age'] as int? ?? 0,
      active: json['active'] as bool? ?? false,
    );
  }

  /// Converts User instance to JSON representation.
  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'surname': surname,
      'age': age,
      'active': active,
    };
  }

  /// Creates a copy of this user with specified fields replaced.
  /// Follows Java builder/copy pattern.
  User copyWith({
    String? name,
    String? surname,
    int? age,
    bool? active,
  }) {
    return User(
      name: name ?? this.name,
      surname: surname ?? this.surname,
      age: age ?? this.age,
      active: active ?? this.active,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is User &&
          runtimeType == other.runtimeType &&
          name == other.name &&
          surname == other.surname &&
          age == other.age &&
          active == other.active;

  @override
  int get hashCode =>
      name.hashCode ^ surname.hashCode ^ age.hashCode ^ active.hashCode;

  @override
  String toString() => 'User($name $surname, age: $age, active: $active)';
}
