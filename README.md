Overview
This project is a web-based application that analyzes documents to extract knowledge points, concepts, and their relationships. It leverages the DeepSeek API to process document content and automatically generate structured relationship data suitable for building knowledge graphs.
Features
Document Upload: Support for PDF and image file uploads
Document Analysis: Extract and summarize key information from documents
Knowledge Relationship Extraction: Identify knowledge points, concepts, and the relationships between them
CSV Export: Generate structured relationship data in CSV format
Neo4j Integration: Ready-to-use data format for importing into Neo4j graph database
Conversational Interface: Chat with the system about uploaded documents or general topics
Technical Details
Backend: Spring Boot Java application
API Integration: DeepSeek AI API for natural language processing
File Processing: PDF text extraction and content management
Data Output: Structured CSV format with knowledge points, concepts and their relationships
Installation
Clone the repository
Configure your DeepSeek API key in DeepSeekController.java
Build the project using Maven:
Apply to DsDemoApplic...
Run
Run the application:
Apply to DsDemoApplic...
Run
Usage
Upload Documents: Use the web interface to upload PDF documents
Document Analysis: Request a summary or analysis of the uploaded content
Knowledge Graph Data: Access the generated relationship data in the Output directory
Neo4j Import: Use the provided Cypher queries to import data into Neo4j:
Apply to DsDemoApplic...
API Endpoints
POST /api/chat: Send messages and optionally upload files
GET /api/files: List all uploaded files
GET /api/history: Get conversation history
Architecture
The application follows a client-server architecture:
Frontend: Simple HTML/JavaScript interface for user interaction
Backend: Spring Boot REST API for file processing and AI integration
External Services: DeepSeek API for natural language processing
Storage: File system storage for uploaded documents and generated data
Data Format
The relationship.csv file contains five columns:
knowledge_id: Unique identifier for the knowledge point
knowledge_name: Name/description of the knowledge point
concept_id: Unique identifier for the concept
concept_name: Name/description of the concept
relation: Description of the relationship between the knowledge point and concept
Dependencies
Spring Boot
DeepSeek API
Apache PDFBox
Hutool HTTP
FastJSON
