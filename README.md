# Simple Bank Implementation
An approach using Spring Boot for a REST Interface

## Getting Started

To be able to compile the following code you will need the following tools:
- [Gradle] (https://gradle.org/) 
- [Lombok plugin] (https://projectlombok.org/)
- [EditorConfig plugin] (http://editorconfig.org/)
- Your favorite IDE (for example [Intellij] (https://www.jetbrains.com/idea/))

### Prerequisites

To test the functionality we recomend using a Chrome/Firefox plugin. 
See [Advanced REST Client for Chrome] (https://chrome.google.com/webstore/detail/advanced-rest-client/hgmloofddffdnphfgcellkdfbfbjeloo)

### Pending improvements:
- Avoid @Getter within AccountsService and instead create method clearAccounts. This prevents direct access to the Repository from the controller.
- Account Service should return an AccountNotFoundException when an account wasn't found on the persistence layer
- Implement HATEOAS for the responses with at least self link in both Accounts and Transfers and link to sender and receiver accounts in TransfersController.
- Current functionality does not allow to rollback a transfer. As a deletion function is not desirable due to tracking purposes, DELETE HTTP Method could be used to execute an inverse transfer.
- Implement GET method for v1/accounts to retrieve a list of all Accounts
- Implement a persistence layer. 
- Take into account ACID atomicity when persisting Accounts after a transfer to avoid consistency issues if an account is updated and not the other.
- Implement auditory fields both in accounts and transfers. Also create an log table storing the different states and dates of a transfer for tracking purposes.

### Notes:
- Synchronization is properly managed for a single JVM/node. If application is deployed in several servers further measures should be implemented, like enforcing persistence synchronization.
- CRUD is not fully implemented for Transfers on purpose. We do not want to allow update or deletion of transfers.
 
 
