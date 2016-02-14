# Facilier

A ClojureScript library/service to test live applications.

## The Elm Architecture

The Elm Language encourages a particular architecture called the
[Elm Architecture](https://github.com/evancz/elm-architecture-tutorial).
Go read the article.

## Predictive Testing

The Elm Architecture is structured around a reactive data flow: there
are three functions (event handlers, step/update, and views) that
receive data (events, actions, and state).

Expressing the flow of the program in layers that communicate with
data makes it easily testable. Each function can be tested in isolation.

Even more, if our data is serializable we can get hold of our user's
session data and use it for testing.

If we think about Virtual DOM as the output to our `view` functions,
then we can think about React as an effect system, that takes the
output of our `views` and handles the DOM side-effect for us.

In a sense it would be a sorts of integration testing for our UI's.

Generative testing where the generators produce your user's data.

Having your user's sessions would let you test code changes against
their previous behavior, and predict if it would break existing user experiences.

## Develop

Facilier is built with [boot](http://boot-clj.com/). All the available
tasks are in `build.boot`. The most commons are `dev` and `auto-test`.
