# floclo

A bot that watches a flowdock chat room and evaluates clojure expressions.

![](https://dl.dropboxusercontent.com/s/jwsmm3fbf17l0jj/Screenshot%202013-12-19%2019.57.22.png)

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

Start it by passing the flowdock org name, room name, and a plugin config file.

```
lein run orgname roomname plugins.clj
```

Example plugins.clj

```clojure
{:main         ; room
 {:clj clojure ; messags tagged #clj will invoke floclo.plugins.clojure/clojure
  :echo echo}} ; messagestagged #echo will invoke floclo.plugins.echo/echo
```

Evaluate any clojure expression (well, any safe expression) by tagging it #clj

```
(+ 1 2 3) #clj
```

```
=> 6
```

Set the FLOWDOCK_TAG environment variable to change the tag it's listening for.

## License

Copyright © 2013 Stephen Olsen

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
