class User {
  final String name;
  final String surname;
  final int age;
  final bool active;

  User({required this.name, required this.surname, required this.age, required this.active});

  factory User.fromJson(Map<String, dynamic> json) => User(
        name: json['name'],
        surname: json['surname'],
        age: json['age'],
        active: json['active'],
      );

  User copyWith({String? name, String? surname, int? age, bool? active}) {
    return User(
      name: name ?? this.name,
      surname: surname ?? this.surname,
      age: age ?? this.age,
      active: active ?? this.active,
    );
  }
}
