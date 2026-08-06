# UBC-CIC-Summer-2026-Hackathon

## Introduction

**Theme** <br>
This Hackathon’s theme is Student Success Tools. Your challenge is to identify a problem that impacts the student experience and a design tool that helps solve it. Whether your focus is academics, physical activity, campus engagement, mental health resources, financial wellness, or another aspect of student life, your solution should address a real need to make a meaningful difference. Think big, be creative, and ask yourself: what challenge do students face, and how can technology help overcome it?

To support participants in turning these ideas into impactful solutions, we are offering a Pre-Hackathon Workshop on the Spec-Driven approach using AWS Kiro. This workshop will introduce a development approach and toolset designed to help teams move efficiently from concept to prototype.

**About Kiro** <br>
We are offering a highly-recommended workshop designed to help you build the skills and knowledge needed to succeed at the Hackathon. The workshop will provide an introduction to Kiro and a guided walkthrough of its key features. Please note that this introduction will not be offered during the Hackathon, so attendees are strongly encouraged to participate in the workshop beforehand.
Spec-Driven Development is an approach that uses AI to guide a structured development process from requirements to design, and then implementation. Instead of jumping straight into coding, teams first define clear requirements, make key architectural decisions, and break features into manageable tasks. This reduces ambiguity, improves consistency, and helps produce higher-quality code, especially for complex projects involving multiple files and contributors.
The workshop will take place on Tuesday, August 4th, from 1:00 pm to 5:00 pm. Please note that registration will take place from 12:30 pm onwards, and the workshop will start at 1:00 pm. Food and drinks will be provided.
**Criteria** <br>
Prior to and throughout the hackathon please keep the following judging criteria in mind as you develop your project. These criteria should guide your approach from initial ideation through to final implementation and presentation.

## Judging
| Criteria  | Description | Points
|----------|-------------|--------|
| **Innovation** | Originality of the idea, novel application of AI, and creative use of existing tools, technologies, or datasets. | 20 |
**Potential Use Cases & Impact** | Identification of target users, practical applications, community impact, feasibility, and tangible benefits. | 20 |
| **Technical Implementation** | Complexity of the AI solution, effective use of Kiro, AWS, and other technical tools, system architecture, and overall technical execution. Judges will assess whether the solution works as intended, demonstrates thoughtful design and implementation, incorporates AI in a meaningful way, and reflects original technical effort beyond basic templates or boilerplate solutions. | 30 |
| **User Interaction & Feasibility** | User experience, interface design, usability, functionality, reliability, and overall system performance. | 15 |
| **Presentation** | Clarity, organization, effectiveness of communication, and persuasiveness of the final presentation. | 15 |
| **Total** |  | **100** |

<br>

## Event Overview 📆
### General Schedule
Tuesday, August 4th, 2026
* 12:30PM: Registration and refreshments
* 1:00PM: Workshop begins
* 5:00PM: Workshop ends

Thursday, August 6th, 2026
* 8:45-9:10AM: Check in and refreshments
* 9:15AM: Introductions and brainstorming
* 9:30AM: Hacking commences
* 12:00PM: Lunch (provided)
* 4:00PM: Hacking ends
* 4:10PM: Presentations start
* 5:30PM: End of Hackathon!

### Item Checklist

#### Required
- UBC student card
- Adapters
- Laptop, charging cables, and your HDMI connector

#### Suggested
- A water bottle
- Reusable coffee mug, containers, and cutlery

### Venue

West Mall Swing Space Building: 2175 West Mall, Vancouver, BC V6T 1Z4.
The registration booth will be set up near the building entrance beside the elevator. Please note that there is construction happening in this area, but you can still access the building.

### Rules

* No plagiarism
* Code must be on GitHub and open sourced
* Any private datasets used must not contain personally identifiable information
* Project design and development must start at the hackathon’s beginning, but preprocessed and structured data is allowed
* All team members must be physically present in the event

### Submission Guidelines

- Team presentation: Total 5 minutes (3 min presentation, 2 min Q&A)
- Make the potential real world impact of this project clear in your presentation
- **DEADLINE**: There is a hard **deadline and requirement** to submit the following onto the [Google Form](https://docs.google.com/forms/d/e/1FAIpQLSdXWJxwWHe1v5pJHsQufT3QgFbu6gXylgoDWcF81Y3F-HBmhA/viewform?usp=dialog) by **4:00PM on August 6th**:
 1. Your slideshow presentation
 2. The link to your public GitHub repository
 3. To judge the technical details of your solution, you must include an architecture diagram (try out draw.io, or any other tool)
 4. Any tools and services you used for your project must be deployed on AWS and outlined in the Google Form
 5. Two to five sentences describing your project and the identification of potential use cases, end users, and overall impact for students.
-  Late submissions will lead to disqualification

### Hackathon FAQs

For frequently asked questions and tips, please visit the Workshop and Hackathon FAQ.

## Resources 💻

### AI Resources

- [Introduction to Generative AI - Art of the Possible](https://explore.skillbuilder.aws/learn/course/external/view/elearning/17176/introduction-to-generative-ai-art-of-the-possible)
- [Planning a Generative AI Project](https://explore.skillbuilder.aws/learn/course/external/view/elearning/17256/planning-a-generative-ai-project)
- - [Foundations of Prompt Engineering](https://explore.skillbuilder.aws/learn/course/external/view/elearning/17763/foundations-of-prompt-engineering)
- [Introduction to LangChain](https://python.langchain.com/docs/get_started/introduction) - LangChain is a framework for developing applications powered by language models
- [Prompt Engineering Best Practices](https://www.youtube.com/watch?v=jlqgGkh1wzY) - Prompt engineering best practices for LLMs on Amazon Bedrock.
- [Anthropic’s Official Documentation](https://docs.anthropic.com/claude/docs/guide-to-anthropics-prompt-engineering-resources) - Anthropic’s Official Prompting Documentation
- [AWS Bedrock Samples Repository](https://github.com/aws-samples/amazon-bedrock-samples) - AWS's Official GitHub Samples Repository
- [AWS GenAI Quick Starts](https://github.com/aws-samples/genai-quickstart-pocs/) - AWS's Quick Starts for GenAI Repository
- [AWS Bedrock PDF Chat](https://github.com/aws-samples/serverless-pdf-chat) - Example of PDF Chat using Amazon Bedrock

### AWS Kiro Fundamentals
- [Kiro Official Website](https://kiro.dev/)
- [Kiro Guides & Tutorials](https://www.kiro.directory/guides/)
- [Kiro GitHub Repository](https://github.com/kirodotdev/Kiro)

---

### Data (extending the LLM)

#### Retrieval-augmented generation (RAG)

Retrieval-augmented generation (RAG) for large language models (LLMs) aims to improve prediction quality by using an external datastore at inference time to build a richer prompt that includes some combination of context, history, and recent/relevant knowledge

- [What Is Retrieval Augmented Generation (RAG)](https://aws.amazon.com/what-is/retrieval-augmented-generation/)

- More in-depth intro [Retrieval Augmented Generation (RAG) for LLMs](https://www.promptingguide.ai/research/rag)

#### Implementing RAG applications on AWS

##### RDS / pgVector:

- [Building AI-powered search in PostgreSQL using Amazon SageMaker and pgvector (Blog post)](https://aws.amazon.com/blogs/database/building-ai-powered-search-in-postgresql-using-amazon-sagemaker-and-pgvector/)
- AWS Samples (GitHub) - [RAG with Amazon Bedrock and PGVector on Amazon RDS](https://github.com/aws-samples/rag-with-amazon-bedrock-and-pgvector)

##### Knowledge Base:

- [Knowledge Bases now delivers fully managed RAG experience in Amazon Bedrock](https://aws.amazon.com/blogs/aws/knowledge-bases-now-delivers-fully-managed-rag-experience-in-amazon-bedrock/)
- [Knowledge Base for Amazon Bedrock](https://aws.amazon.com/bedrock/knowledge-bases/) - [Documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/knowledge-base.html)

##### OpenSearch:

- [Amazon OpenSearch Service’s vector database capabilities explained](https://aws.amazon.com/blogs/big-data/amazon-opensearch-services-vector-database-capabilities-explained/)
- [Build scalable and serverless RAG workflows with a vector engine for Amazon OpenSearch Serverless and Amazon Bedrock Claude models (Blog post)](https://aws.amazon.com/blogs/big-data/build-scalable-and-serverless-rag-workflows-with-a-vector-engine-for-amazon-opensearch-serverless-and-amazon-bedrock-claude-models/)

### Agents for Bedrock

Enable generative AI applications to execute multistep tasks across company systems and data sources

- [User Guide](https://docs.aws.amazon.com/bedrock/latest/userguide/agents.html)
- [Demo Video - Agents for Amazon Bedrock ](https://www.youtube.com/watch?v=UcehCSSOMQA)
- [Amazon Bedrock Agents Quickstart](https://github.com/build-on-aws/amazon-bedrock-agents-quickstart) - Functional code example
- [Build a foundation model (FM) powered customer service bot with agents for Amazon Bedrock](https://github.com/aws-samples/agentsforbedrock-retailagent)

### AWS Basics

- [AWS Cloud Essentials](https://aws.amazon.com/getting-started/cloud-essentials/)
- [Architecting on AWS - Online Course Supplement](https://explore.skillbuilder.aws/learn/course/external/view/elearning/8319/architecting-on-aws-online-course-supplement)
- [AWS Serverless Land](https://serverlessland.com/) - AWS Serverless examples, patterns, documentation and guidance.

