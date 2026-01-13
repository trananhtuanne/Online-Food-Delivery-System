# Food Delivery App - README

## Prerequisites

### Java Installation

This application requires Java Development Kit (JDK) version 11 or higher.

#### Installing JDK on Windows

1. Download the JDK from the official Oracle website or AdoptOpenJDK:
   - Visit https://adoptium.net/ (recommended for open-source JDK)
   - Download the latest LTS version (e.g., Temurin JDK 17)

2. Run the installer and follow the on-screen instructions.

3. Set up environment variables:
   - Open System Properties > Advanced > Environment Variables
   - Under System Variables, click New:
     - Variable name: JAVA_HOME
     - Variable value: C:\Program Files\Java\jdk-17.x.x (adjust path to your JDK installation)
   - Edit the PATH variable and add: %JAVA_HOME%\bin

4. Verify installation:
   - Open Command Prompt and run: `java -version`
   - You should see the Java version displayed.

## How to Use the Code

### Project Structure
- Source code: `src/main/java/com/doan/FoodDeliveryApp.java`
- Data file: `food_delivery_app_data.bin` (created automatically)

### Compiling the Application
1. Open Command Prompt and navigate to the project root directory:
   ```
   cd "c:\Users\PC ACER\Videos\java project\doan"
   ```

2. Compile the Java file:
   ```
   javac -cp . src/main/java/com/doan/FoodDeliveryApp.java
   ```

### Running the Application
1. After compilation, run the application:
   ```
   java -cp . com.doan.FoodDeliveryApp
   ```

2. The GUI will launch, allowing you to manage food delivery operations.

### Features
- User management
- Food item catalog
- Order processing
- Complaint handling
- Data persistence

## Troubleshooting
- Ensure JDK is properly installed and JAVA_HOME is set.
- If compilation fails, check for syntax errors in the code.
- Make sure all dependencies are available (this app uses standard Java libraries).
