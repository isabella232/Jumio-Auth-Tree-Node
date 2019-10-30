# Forgerock & Jumio Implementation Guide

This is a reference manual and configuration guide for the Netverify integration with Forgerock. It describes how to create the flow tree for users to pass through the Netverify journey.

## Table of Contents

- [What is Jumio?](#What-is-Jumio?)
- [What is in this package?](#What-is-in-this-package?)
- [Installation Steps](#Installation-Steps)
- [Login to Forgerock](#Login-to-Forgerock)
- [Select a Realm](#Select-a-Realm)
	- [Create a Realm](#Create-a-Realm)
- [Setting up Netverify](#Setting-up-Netverify)
	- [Netverify Settings](#netverify-settings)
	- [Creating a Login Workflow](#creating-a-login-workflow)
	- [Testing the Tree](#testing-the-tree)


## What is Jumio?

Jumio's end-to-end identity verification and authentication solutions fight fraud, maintain compliance and onboard good customer's faster.

## What is in this package?

Once built into Forgerock, two nodes will be available.

- Jumio Initiate node
- Jumio Decision node

## Installation Steps

1. Download the latest version of the Jumio integration jar from . ..
2. Copy the jar file to the **WEB-INF/lib/** folder where AM is deployed.
3. Restart the AM for the new plug-in to become available.

**Jumio Initiate Node**: Initiates the Jumio transaction and redirects the user to the Netverify journey.

**Jumio Decision Node**: Retrieves transaction result, updates the shared state and directs the user based on the outcome of the Netverify transaction.

## Login to Forgerock

You should already have a Forgerock instance and login information at this point. If you do not have access to Forgerock yet, please contact your point-of-contact at Forgerock.

![Jumio](/guideimages/login0.jpeg)

Log in using your given user ID and password.

## Select a Realm

Once you're logged in, you'll reach the Realm selection page. Select a Realm that you would like to add the **Netverify** workflow to.

If you have not created a realm yet, you'll need to do so now.

### Create a Realm

To create a new Realm, click on the **+ New Realm** button.

![Jumio](/guideimages/realm0.jpeg)

Fill out all the required information you need for your Realm instance.

For a test Realm to use solely with Netverify, you will only need to have a name for the Realm.

![Jumio](/guideimages/realm1.jpeg)

Click **Create** button once you've filled out all necessary information.

## Setting up Netverify

This next section will focus on setting up Netverify settings and getting a login workflow that includes Netverify journey.

### Netverify Settings

Navigate to your desired Realm Overview page. You can choose the realm you want by clicking on **REALMS** in the menu bar at the top of the page.

![Jumio](/guideimages/setting0.jpeg)

Once on the **Realm Overview** page, navigate to the **Services**, found on the leftside menu.

![Jumio](/guideimages/setting1.jpeg)

You should see **Jumio Service** listed on this page. If you do not, you have not installed the Jumio package yet.

Select **Jumio Service** and fill out all the information on this page.

![Jumio](/guideimages/setting2.jpeg)

**Save Changes** once you've filled out the page.

### Creating a Login Workflow

We will not create a basic login workflow that includes the Netverify journey.

On the leftside menu, click on **Authentication** and select **Trees**.

Click **+ Create Tree** at the top of the page.

![Jumio](/guideimages/tree0.jpeg)

On the next page you'll name the tree. We'll name ours **netverify-sample-workflow**. Click **Create** once complete.

![Jumio](/guideimages/tree1.jpeg)

Next, you'll be brought to the tree creation page. The default tree provided already contains a **Start** and **Failure** node.

![Jumio](/guideimages/tree2.jpeg)

To start the workflow, we'll add **Username Collector**, **Jumio Initiate Node**, **Jumio Decision Node** and a **Polling Wait Node**.

Connect **Start** to the **Username Collector** which connects to the front of the **Jumio Initiate Node**.

**Jumio Initiate Node** *True* will go to a **Polling Wait Node**. *False* will go to **Failure**.

The end **Polling Wait Node** will connect to the **Jumio Decision Node**.

Our tree should look similar this.

![Jumio](/guideimages/tree3.jpeg)

Now, we'll configure the **Polling Wait Node**. 

Select the first node and a menu should appear on the righthand side.

Set **Seconds To Wait** to **30**. 

This will make the system wait 75 seconds after the Netverify workflow has completed to try to ping Jumio for results using the **Jumio Decision Node**.

![Jumio](/guideimages/tree4.jpeg)

For the second part of the tree, pull the following nodes into the tree: **Polling Wait Node**, **Create Password**, **Provision Dynamic Account**, **Success**.

Connect the **Jumio Decision Node** points as follows:

- *Failed* and *Fraud* to **Failure**
- *Unreadable* and *Unsupported* to beginning of **Jumio Initiate Node**
- *Pending* to the new **Polling Wait Node** which connects back to the beginning of the **Jumio Decision Node**
- *Success* connects to **Create Password**, then **Provision Dynamic Account**, then to **Success*

The second **Polling Wait Node** can be set to 30 seconds. This will loop the **Jumio Decision Node** until the results from Netverify are no longer *Pending*.

This portion of the free should look similar to this:

![Jumio](/guideimages/tree5.jpeg)

You can then click on the **Auto Layout** icon near the top and your tree will be straightened out. The end of your tree should look as follows.

![Jumio](/guideimages/tree6.jpeg)

Your Netverify tree should now be complete and you should can test the workflow.

### Testing the Tree

You can test the tree by simply typing your server url and include the url parameters of the realm and service you created the tree under.

Example:
https://domain.com?realm=MYREALM&service=MYSERVICE
