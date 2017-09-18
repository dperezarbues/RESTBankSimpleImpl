# Simple Bank Implementation
An approach using Spring Boot for a REST Interface

##Project configuration
To be able to compile the following code you will need:
- Lombok plugin 
- EditorConfig plugin

##Pending improvements
- Avoid @Getter within AccountsService and instead create method clearAccounts. This prevents direct access to the Repository from the controller.
- Account Service should return an AccountNotFoundException when an account wasn't found on the persistence layer
- Implement a persistence layer. 
- Take into account ACID atomicity when persisting Accounts after a transfer to avoid consistency issues if an account is updated and not the other.
- Implement auditory fields both in accounts and transfers. Also create an log table storing the different states and dates of a transfer for tracking purposes.

 
