# SmartFox Server Tools library

This is a small library helping you to implement your own extension for SmartFox Server

## Release notes

* 0.9 - Support for maps serialization.
* 0.7 - Add support for dates serialization.
* 0.6 - Externalize the serializer API
* 0.5 - SFSSerializer now supports custom serialize post/pre processors & custom field serializer/deserializer.
* 0.3 - SFSSerializer support for enums / list of enums.

## Supported SmartFox versions

* 2.3.0, 2.4.0

## What it gives

You can easily serialize and deserialize your objects from/to ISFSObject:

```java
class MyObject extends AbstractTransportObject {
    private Integer field;
}
// ...
MyObject obj = new MyObject();
obj.field = 200;

obj.toSFSObject(); // gives instance of ISFSObject
obj.updateFromSFSObject(isfsObject); // updates all the mapped fields recursively

```

You can check the access to your handlers implementing your own auth logic:

```java
    class MyAuthChecker extends AuthService {
       @Override
       public void check(User user) throws UnauthorizedException {
          if(!user.getName().equals("Vasya")){
            throw new UnauthorizedException("You don't have the permission to access this handler!");
          }
       }
    }
    // ...

    @Security(authStrategy = MyAuthChecker.class)
    public class GetPrivateDataHandler extends AbstractClientRequestHandler {
        @Override
        public void doHandle(User user, ISFSObject inputData) {
            // some logic
        }
    }

```

## How to use

First you need to setup the environment to use Maven as a builder for your SmartFox server extension. You can follow
[this tutorial](http://smecsia.me/blog/74/Developing+the+extension+for+Smartfox+server+using+Maven%2C+Spring%2C+Hibernate+and+Kundera)
to see how to do it. Or you can start with [this example](https://github.com/smecsia/smartfox-extension-example). Then
 you need to add the following repository and dependency to your pom.xml:

```xml
    <!-- ... -->
    <dependencies>
        <dependency>
            <groupId>me.smecsia.smartfox</groupId>
            <artifactId>smartfox-tools</artifactId>
            <version>0.9</version>
        </dependency>
    </dependencies>
    <!-- ... -->
    <repositories>
        <repository>
            <id>smecsia.me</id>
            <name>smecsia public repository</name>
            <url>http://maven.smecsia.me/</url>
        </repository>
    </repositories>
    <!-- ... -->
```

And then you can use the features described above.


Copyright (c) 2012 smecsia

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.