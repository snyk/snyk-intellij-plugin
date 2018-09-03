# Going Native

Stackoverflow helped!
https://stackoverflow.com/questions/3584883/where-is-the-jetbrains-intellij-openapi-documentation

Check out intellij community
looked at Ant plugin, saw

- contentManager.getFactory.createContent
- setToolbar & setContent
- toolbar panel
- transferProvider ???
- treeModel
- all using `AnAction` - as per [docs](???) which don't mention this usage at all
- Disposable and DataProvider
- treeExpander, which was tied to DataProvider.getData
- DefaultTreeModel, Tree, TreeUtil.installActions, TreeSpeedSearch
- AntExplorerTreeBuilder (which holds a ref to myTree)

(split into discussions of A: The tree model and B: The actions model)

Tree only mentioned in docs under "Trees & Lists" in a single sentence as a replacement for JTree
 
TreeSpeedSearch only mentioned in passing
 
ToolbarDecorator was **not** helpful and lacked context - and isn't used in the Ant Plugin

http://www.jetbrains.org/intellij/sdk/docs/user_interface_components/lists_and_trees.html

Action system docs only useful after seeing how it related to trees & toolbars
http://www.jetbrains.org/intellij/sdk/docs/tutorials/action_system.html
