# SystmOne Document Processing System

This Java application automates the process of extracting and saving documents from the SystmOne medical records system. The system uses image recognition technology to interact with the SystmOne interface, enabling efficient batch processing of medical documents while maintaining data integrity and organization.

## Features

The system provides robust document processing capabilities including:

- Automated document navigation and extraction
- Location-aware processing (supports Denton and Wootton configurations)
- Intelligent UI state tracking and verification
- Organized output structure with timestamped folders
- Comprehensive error handling and recovery
- Detailed processing statistics and summaries
- Graceful shutdown mechanism

## Prerequisites

Before running the application, ensure you have:

- Java Development Kit (JDK) 11 or higher
- Maven 3.6.0 or higher
- Active SystmOne installation with appropriate access permissions
- Sufficient disk space for document storage
- Required image patterns in the configured image directory

## Installation

1. Clone the repository:
```bash
git clone [repository-url]
cd systmone-document-processor
```

2. Build the project using Maven:
```bash
mvn clean install
```

## Configuration

The application uses `ApplicationConfig.java` for system-wide settings. Key configurations include:

- Image directory path: Location of pattern recognition images
- Output base path: Base directory for saved documents
- Location-specific settings: Denton/Wootton configurations
- Timing parameters: Delays and timeouts
- Similarity thresholds: Pattern matching sensitivity

## Usage

1. Ensure SystmOne is open and logged in
2. Navigate to the document view you wish to process
3. Run the application:
```bash
mvn exec:java
```

The application will:
1. Initialize system components
2. Determine the current location (Denton/Wootton)
3. Begin processing documents sequentially
4. Save documents to timestamped folders
5. Generate a processing summary upon completion

## Output Structure

Documents are saved in the following structure:
```
output/
└── YYYY-MM-DD_HH-mm-ss/
    ├── Document1.pdf
    ├── Document2.pdf
    └── processing_summary.txt
```

## Error Handling

The system implements comprehensive error handling:

- Initialization failures are reported with specific error messages
- Document processing errors are captured and logged
- Navigation failures trigger automatic recovery attempts
- Processing statistics track successful and failed operations

## Development

The system is built with a modular architecture:

- `Application.java`: Main entry point and workflow coordination
- `SystemComponents.java`: Core component management
- `SystmOneAutomator.java`: SystmOne interface interaction
- `UiStateHandler.java`: UI state management and verification
- `DocumentProcessor.java`: Document processing workflow
- Supporting classes for initialization, error handling, and statistics

## Building

To build the application:

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Create executable JAR
mvn package
```

## Troubleshooting

Common issues and solutions:

1. Pattern Recognition Failures
   - Verify image patterns match your SystmOne version
   - Check similarity thresholds in configuration
   - Ensure proper screen resolution and scaling

2. Navigation Errors
   - Confirm document view is correctly positioned
   - Verify scrollbar visibility and accessibility
   - Check for SystmOne window focus

3. Save Failures
   - Verify write permissions in output directory
   - Ensure sufficient disk space
   - Check for file system access restrictions

## Support

For technical support or bug reports:
1. Check the troubleshooting guide above
2. Review error logs in the application output
3. Contact the development team with specific error messages and timestamps

## Best Practices

For optimal operation:
1. Close unnecessary applications before running
2. Ensure SystmOne is the active window
3. Avoid interacting with the system during processing
4. Regularly monitor the output directory
5. Review processing summaries for potential issues

## Security Note

This application handles medical documents. Always ensure:
- Proper access controls are in place
- Output directories are properly secured
- System use complies with local data protection policies

## Contributing

When contributing to this project:
1. Follow the established code structure
2. Maintain comprehensive error handling
3. Update documentation for new features
4. Add appropriate test coverage
5. Follow security best practices

For substantial changes:
1. Discuss changes via issue tracker
2. Create feature branches
3. Submit pull requests with detailed descriptions
4. Include updated documentation