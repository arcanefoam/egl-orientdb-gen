# EVl Templates

The EVL templates use the EMF metamodel structure and the added EAnnotations to generate the OrientDB code. The templates can be found in the org.eclipse.epsilon.orientdb.data.generation package in the org.eclipse.epsilon.orientdb.data project.

I tried to make the templates as configurable as possible and to avoid hardcoded names as much as possible. However, more work could be done to improve them.

# EcoreHelper.eol

This files provides some basic helper operations, mainly to read the EAnnotation information. 

# CommonTemplates.egl

There is a very clear naming convention/pattern when generating Java code. This template provides common template operations to create getter/setter methods (and their JavaDoc), to create getter return types and the list of parameters for a setter method, etc. There is little documentation as the names (I hope) are self explanatory.

# EClass2Vertex.egl

This template is used to generate the Vertex classes used for nodes in the DB Schema. The template generates an Interface that uses tinkerpop @Property and @Adjacency annotations to define the node attributes and relations. Note that vetex and edge class names are used by defining a static field called "TRACE_TYPE". The use of a static field provides some benefits when writting the code manually, but it looses value when the code is generated. However, this templates where created using an existing reference implementaiton so they reflect this, and other, "best practices" that might not be so importnat for generated code. 

The templates provides different getter/setter implementations depending on the multiplicity of the relationship.

# EReference2Edge.egl

This template is used to generate the Edge classes used for edges in the DB Schema. Again, tinkerpop annotations are used to define the edge vertices. Note that as in the previous template, the interface name is defined by

    eRef.getJavaTypeName(typePrefix, typeSufix)

In the current implementation nodes are prefixed with V and edges with E, but this helper method allows more flexibility and is used throughout the templates to provide consistency. 

# EClass2OrientImpl.egl

This template generates an implementation of the Data Model interfaces using a delegator pattern. The delegate is the node class created by EClass2Vertex.egl. This template also provides alternative implementations for attributes and references with multiplicity values > 1. The main reason is that the OrientDB API uses Iterable<X> for multi-valued relations, so we need to turn this into a List<X> as defined by the metamodel API.

# EPackage2OrientDbDAO.egl

This template generates the Schema creation and the CRUD methods. For the Schema creation the template used the annotation information (e.g. to crate indices). Four CRUD methods are created for each ECLass: 
* create<X>(args...)
* delete<X>(Vertex v)
* update<X>(Vertex v)
* get<X>ById(Object id)

and if the EClass defines an index and additional get<X>ByIndex is created too. In this paricular implementation all the CRUD operations are atomic, this is, the operation gets the Graph reference, operates, optionally commits and then shutsdown the graph. This means that any class usinf the DAO will work with detached objects. For this reason, a *getManager()* method was generated in order to allow other classes to control the DB connection. 
    
# EcoreToOrientDB.egx

This is the EGX program that invokes each of the EVL templates on the approiate metamodel element. Some global paramters are defined in this program (e.g. the base qualified package) for all EVL templates to share. 
