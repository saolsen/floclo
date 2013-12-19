# floclo

A bot that watches a flowdock chat room and evaluates clojure expressions.

## Usage

Set env vars for flowdock authentication.
```
FLOWDOCK_USERNAME and FLOWDOCK_PASSWORD

```
You must also have a `~/.java.policy` file in your home directory. eg

```
grant {
  permission java.security.AllPermission;
};
```

Start it by passing the flowdock org name and room name.

```
lein run orgname roomname
```

Evaluate any clojure expression (well, any safe expression) by tagging it #clj

```
(+ 1 2 3) #clj
```

```
=> 6
```

## License

Copyright © 2013 Stephen Olsen

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
