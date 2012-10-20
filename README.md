# SmartFox Server Tools library

This is a small library helping you to implement your own extension for SmartFox Server

## What it gives

You can easily serialize and deserialize your objects from/to ISFSObject:

```java
class MyObject extends AbstractTransportObject {
    @SFSSerialize
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

## Supported SmartFox versions

* 2.3.0

## Changelog
