# EVL OrientDB CRUD Generator

Epsilon EVL demo project to generate OrientDB CRUD implementation using the Graph API

##  DISCLAIMER

I am **NOT** an experienced DB programer and thus the generated code will most likely be "ugly"; incorrect use of API, incorrect separation of concerns, incorrect use of Frames and Pipes, etc. The purpose is to ilustrate how EVL can be used to generate CRUD code for a DB.

## Requirements

You need an Eclipse IDE with EMF and Epsilon installed as minimum. The easiest way is to get one from the Epsilon website, [here](https://eclipse.org/epsilon/download/).

You need then to import the plugins in this repo to your Eclipse workspace:
  
    File -> Import -> Existing Projects into Workspace (follow the instructions)
    
**Note:** I have not commited any of the generated code so after importing your projects will most likely won't compile and you will get a lot of 'The import xxx cannot be resolved'.


## Runing the generators

We need to generate two pieces of code: the Data Model (defined by the business layer) and the DB code (used in the data layer). The Data Model code is generated using EMF's generator model. 

1. Open the ExecutionTrace.genmodel (org.eclipse.epsilon.orientdb.business/model). Right click on the firt element in the tree view and select: Generate Model Code. A bunch of files should have been generated in the emf-gen folder. 

2. Locate the EcoreToOrientDB_Demo.launch at the root of the org.eclipse.epsilon.orientdb.data project. Right click on it and select Run as.. > EcoreToOrientDB_Demo. You should see a *GenDone* message in the console and a bunch of pacakges and files should have been generated in the src-gen folder.

## The data model

For CRUD generation we need to model the data model. In this case we used an EMF metamodel to do so. The exampe metamodel is in the *bussiness* 
