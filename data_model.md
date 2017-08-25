# The Data Model

The Data Model defines the nodes and relations that we want to use in the DB. To model the Data Model we havge used an EMF metamodel. If you don't have experience with EMF, just think of it like a UML subset of class diagrams.

## The Nodes

Each class in the metamodel represents a Node in the graph. In this case these nodes are ExecutionContext, ModelElement, ModuleElement, Property and Trace. Note that there is an additional EClass called TraceElement which is a super class for all the other classes.

### The TraceElement EClass

The purpose of this class is to allow the Data Model to "play nice" with implementations that provide unique ids for their elements, for example the OrientDB. For this, the TraceElement EClass has a single attribute called id, which is read-only (Changeable: false) and that has a EJavaObject EAttribute Type (java.lang.Object). Given that the EClass is defined as an interface this will result in the generation of a single method *Object getId();* (which will be inherited by all EClasses). The OrientDB generated implementation classes will delegate this method to the DB objects.

### OrientDB indices

In order to define an index for a node, the respective EClass should have an EAnnotation with its Source set to *https://eclipse.org/epsilon/incremental/OrientDbIndex*

The OrientDbIndex annotation allows information relevant to [Index](http://orientdb.com/docs/last/Indexes.html) definitions. To define an index for an EClass, the annotation must reference the EAttribute that will be used as an index and must include a details entry where the key is "type" and the value is one of the supported index types.

For example the ExecutionContext EClass, defines the *scriptId* as the node index and the *type* is **NOTUNIQUE_HASH_INDEX**. 

### Attributes

All attributes defined in the class will be added as Properties to the nodes.

This is all the information needed to create the Shcema node definitions.

**Note:** All EClasses could be marked as interfaces if your business code doesn't want to use the generated EMF Java Classes.

## The Edges

EMF models support bi--directional relationships between classes by defining one EReference as the oppoiste of the other. In OrientDB, a single edge is needed to define the relationship. In order to avoid double edges EReferences that should be used as edges must be annotated and the source must be *https://eclipse.org/epsilon/incremental/OrientDbGraph*. This annotation should have a single value with key "edge" and value "true".

And that is pretty much it. I planned an additional annotation for Attributes that use a type other than the standard EMF (Java) types so the mapping between the specifc type and the OrientDB supported types can be defined... but didn't implement it as atm was not needed.

# The GenModel

Altough we can not modify the EMF generation templates (at least not easily), we need to configure the GenModel to generate code that plays nicely with our applications. The GenModel has mnay configuration parameters which I will not explain in detail. I will just point out at the specific changes I used and their effect on the generated code. 

## Base pacakge

We want to make sure the generated code is inside the correct package. For this, expand the tree in the GenModel view and select the EPackage.

    Right Click -> Show Properties View

On the Properties View expand the **All** section, there should be a *Base Package* property. Make sure it matches your desired paakge. In this case the value is: org.eclipse.epsilon.orientdb.business

## General Settings

Click on the GenModel node in the GenModel view (the root node). Expand the **All** section. The preffered settings are:

* Bundle Manifest:	false
