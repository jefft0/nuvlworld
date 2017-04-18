Nuvl World
==========

Nuvl World is a Java application which represents knowledge about events and objects in
the world and uses Nuvl to resolve conflicts in order to present a consistent view.

Note: This application is still in the alpha development stage.

Resolving conflicting events
----------------------------

Nuvl not only represents the time and place of an event but also the rules under which two events may conflict.
For example, the application may show you attending a meeting in Barcelona, but also attending a
birthday party at friend's house. If the Nuvl knows your friend's house is in Germany,
then it has rules to show that Barcelona and Germany are separate locations and you can't attend events in two separate places at the same time. Using
[assumption-based argumentation](http://www.doc.ic.ac.uk/%7Eft/publications.html), the application
shows the argument for you being in Barcelona and the argument for you being at your friend's house
and why they contradict. This lets you better see how to resolve the conflicting events.
