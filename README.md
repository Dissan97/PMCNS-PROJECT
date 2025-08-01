#  Simulation of the Workflow of a Web App
    Regardless of the paradigm adopted in modern web application architectures (e.g., <br>
    webservices, microservices, serverless) software developers must describe the business <br>
    logic of the apps through workflows representing the sequence of execution of <br>
    the tasks. Depending on its layout, mapping a workflow to a queuing network model <br>
    may not be an easy task. More precisely, we refer to the case in which a request after <br>
    being executed by a station and flowed through the model, returns to that station and <br>
    requires service times and routing very different from the ones required previously. <br>
    The problem we face arises because JMT does not store the execution history of a <br>
    request in terms of paths followed between the various resources. To solve this, we <br>
    use the class identifier parameter Class ID associated with each running request to <br>
    track only its recent execution history. <br>
    In fact, each request in execution is assigned a Class ID that is used to describe <br>
    its behavior and characteristics, such as, type (open or closed), priority, mean and <br>
    distribution of service times. Routing algorithms are defined on a per-class basis. <br>
    Of fundamental importance to the problem approached is that a request may change <br>
    Class ID during its the execution flowing through a Class-Switch station or <br>
    when a specific routing algorithm is selected. Therefore, with the use of the Class ID <br>
    parameter we can know the last station visited by a request and the path followed. <br>
    To describe this technique we consider a simplified version of the e-commerce
    application of an online food shopping company. The web services of the software platform <br> 
    are allocated on two powerful servers, referred to as Server A and <br>
    Server B,of the private cloud infrastructure. the figure below shows the layout of the <br>
    data center with the paths followed by the requests and the relative Classes. The <br>
    sequence of execution of the paths for each request coincides with the numbers of <br>
    the Class IDs. <br>
    ![system](https://github.com/Dissan97/PMCNS-PROJECT/blob/master/imgs/system.png)
    Server A is a multicore system that execute several services of Front-End. <br>
    Among them are:customer authentication,administrative and CRM processes,inter <br>
    action with the payment service (for the strong authentication for payments), check <br>
    out operations with the update of the DB, invoice generation, shipping and tracking <br>
    services, and update of customer data.Server B is a multiprocessors blade system, <br>
    highly scalable, fault-tolerant, with redundant configuration for continuous availability, <br>
    equipped with large RAM memory and SSDs storage for the DBs. Among the <br>
    most important services allocated are those for browsing the catalog, processing the <br>
    shopping cart, and managing the DBs of products and customers. To provide the <br>
    minimum Response time to customers, an in-memory DB is implemented to <br>
    dynamically cache each customer’s most recent purchases. <br>
    A third server (Server P), located in the data center of an external provider, is
    used for payment services. <br>
    To reduce the complexity of the description we have considered a simplified <br>
    version of the workflow of the e-commerce app, consisting only of the <br>
    services that are needed to describe the problem approached and its solution. <br>
    the figure below shows the services considered and the servers where are stored.

