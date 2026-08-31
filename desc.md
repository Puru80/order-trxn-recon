## The Scenario

An online store has two systems that should agree with each other, and do not.

- **`orders.csv`** is exported from the store's order system. It is what the store believes it sold.
- **`payments.csv`** is exported from its payment processor. It is what actually got charged, refunded, or settled.

In theory, every completed order has exactly one matching payment for the right amount. In practice, the two files disagree in a number of ways, and nobody currently knows where the money is leaking.