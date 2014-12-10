# lesezeichen

A simple url collecting application using datomic as backend storage and Om as frontend framework.

Visit a live application [here](https://bookie.polyc0l0r.net/).

## Usage

For quick intro compile clojurescript

```
lein cljsbuild once dev-auth dev-client
```
Fill in relevant data in 'opt/server-config.edn' and run it with
```
lein run opt/server-config.edn
```

Then visit <http://localhost:8087/>

## License

Copyright © 2014 Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
