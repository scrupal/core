[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Build Status](https://travis-ci.org/scrupal/scrupal-core.svg?branch=master)](https://travis-ci.org/scrupal/scrupal-core)
[![Coverage Status](https://coveralls.io/repos/scrupal/scrupal-core/badge.svg?branch=master&service=github)](https://coveralls.io/github/scrupal/scrupal-core?branch=master)
[![Release](https://img.shields.io/github/release/scrupal/scrupal-core.svg?style=flat)](https://github.com/scrupal/scrupal-core/releases)
[![Join the chat at https://gitter.im/scrupal/scrupal-core](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/scrupal/scrupal-core?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Stories in Ready](https://badge.waffle.io/scrupal/scrupal-core.svg?label=ready&title=Ready)](http://waffle.io/scrupal/scrupal-core)

# scrupal-core

[![Join the chat at https://gitter.im/scrupal/scrupal-core](https://badges.gitter.im/scrupal/scrupal-core.svg)](https://gitter.im/scrupal/scrupal-core?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
This is the core (most fundamental) module of Scrupal, a reactive content management system and
web framework based on Play Framework. It provides a content management system based on asynchronous processing,
non-blocking I/O, the actor model, publish and subscribe events and with sufficient hooks and modularity for extension.

## Design
Scrupal has scruples, and it is opinionated about the technologies and designs used. The project strives to make
best-of-breed decisions in each area, the value is in having made a decision as much effort can be lost in debating
the relative merits of various framework choices (of which there are many). This framework is aimed at making the
production of modular, scalable, and feature rich content management systems easier for Scala programmers. To extend it,
you need to be a Scala programmer. Consequently, several decision decisions have been made:
* Scala is the programming language of choice and we do not want to require other languages
* Since Scala is the language, we also use scalatags for HTML and Scala.js for Javascript portions.
* Templating languages are deprecated; write Scala and scalatags instead
* Core data is stored relationally via Slick and Slickery (typically with H2 or Postgres)
* Content can come a wide variety of sources include SQL, NoSQL and web sources. The Source API abstracts away the differences
* Play Framework is the HTTP/Routing/Handler infrastructure but as Play migrates towards Akka-HTTP, we may avoid Play altogether in a future release.
* Scrupal supports users, authorization and authenticaiton out of the box and that will be provided by Silhouette
* Scrupal's content is based on web components, specifically Polymer.

