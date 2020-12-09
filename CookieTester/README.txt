
These class files make up a client to test CS 353 Java Assignment 3

The client is a small Java program to test a web server cookies.

The cookie test sends the server an HTTP request, checks if the response is the index.html, and that
the header contains a correctly formatted cookie with the correct data and time.
The tester then sends a 2nd message to the server with a cookie, and checks if the response contains
the correct message as well as a correctly formatted cookie with the correct date and time. 

To run:

   tar xvf CookieTester.tgz

   cd CookieTester

   java CookieTest <server> <port>



 
