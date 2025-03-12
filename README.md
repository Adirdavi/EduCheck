# EduCheck

## Overview
EduCheck is an auto-grading tool designed to streamline the evaluation of educational coding exercises. It automates the grading process, providing immediate feedback to students and reducing the manual workload for educators.

## Key Features
* **Automated Grading**: Instantly assesses coding assignments based on predefined criteria.
* **Immediate Feedback**: Provides students with real-time insights into their performance.
* **Customizable Tests**: Allows educators to define specific test cases and grading rubrics.
* **Detailed Reports**: Generates comprehensive reports highlighting common errors and areas for improvement.

## Technologies Used
* **Programming Language**: Kotlin
* **Build Tool**: Gradle
* **Database**: Firebase Realtime Database
* **Testing Framework**: (Specify the testing framework used, e.g., JUnit)

## Registration and Login
* Each user (teacher or student) logs in using a username and password or registers.

## Creating Tests in the App
* Teachers can create open-ended or multiple-choice tests and save them securely in the cloud.

## Statistical Analysis for Multiple-Choice Questions
* Displays graphs with success rates, average scores, and problematic questions.

## Teacher Test Grading
* Teachers can grade tests, add comments, and store results securely in the cloud.

## Grade Average for Students
* Students can view their grade average and track progress through a graph.

## Report Test Errors
* Students can notify teachers of errors in test questions, and teachers can update them.

## The Server's Uses
Use that will be made on the server:
* Backup and transfer data between users with Firebase Realtime Database.
* Exam handling.
* Grade tracking.

For example:
* Save exams.
* Add users.

## Installation
To set up EduCheck locally:
1. **Clone the repository:**
```bash
git clone https://github.com/Adirdavi/EduCheck.git
```

2. **Navigate to the project directory:**
```bash
cd EduCheck
```

3. **Build the project using Gradle:**
```bash
./gradlew build
```

## Usage
After installation:
1. **Run the application:**
```bash
./gradlew run
```

2. **Access the user interface** (if applicable) or follow the command-line prompts to upload and grade coding exercises.

## Contributing
We welcome contributions to enhance EduCheck:
1. **Fork the repository.**
2. **Create a new branch** for your feature or bug fix:
```bash
git checkout -b feature-name
```

3. **Implement your changes** and commit them with descriptive messages.
4. **Push your branch** to your forked repository:
```bash
git push origin feature-name
```

5. **Open a pull request** detailing your changes and the motivation behind them.

## License
This project is licensed under the MIT License.

## Acknowledgements
We extend our gratitude to the contributors and the open-source community for their support and collaboration.
