# SmartFox Server Tools library

This is a small library helping you to implement your own extension for SmartFox Server

## Release notes

* 0.5 - SFSSerializer now supports custom serialize post/pre processors & custom field serializer/deserializer.
* 0.3 - SFSSerializer support for enums / list of enums.

## Supported SmartFox versions

* 2.3.0, 2.4.0

## What it gives

You can easily serialize and deserialize your objects from/to ISFSObject:

```java
class MyObject extends AbstractTransportObject {
    private Integer field;
    public Integer getField(){
        return field;
    }
    public void setField(Integer field){
        this.field = field;
    }
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
            <version>0.5</version>
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


