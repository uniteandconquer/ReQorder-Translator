# ReQorder-Translator
A translation utility for the ReQorder application

-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

Java 11 or higher needs to be installed.

For Windows:

double click the start.bat file

For Mac and Linux:

make the 'start.sh' file executable and run it, or open a terminal window in the 'ReQorder Translator' folder and type:

java -jar bin/translator.jar

(you can also type: start.sh)


-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------


Important notes for the translator:

In order to preserve the functionality of the ReQorder application, some rules of thumb have to be adhered to:

- You should only translate the actual words, any symbols or code shoud not be altered

- %% are placeholder symbols for dynamically generated text 

- <br> and <br/> are line breaks 

- <html> and <div> are used for alligning text in the dialogs

- Single quotes ( ' ) are used to insert text entries into databases in some cases, so it's imperative to not ommit them or add them unless neccesary

- New lines are not allowed, if you hit enter/return you will automatically be directed to the next empty entry. You can go back to the last edited entry by clicking the button at the bottom

Thank you and good luck, scroll down further for more information on how to get started.


On the left side you will see a list of keys, each key has a corresponding line of text that you can translate.

The translated files can be found in the 'ReQorder Translator/translated' folder.

The source file is in English by default. You can choose to load a different source file if you wish. It is imperative however that the source file contains all the keys so it's probably best to use the supplied 'Language.properties' file.

When you first begin, the target file for your language will probably not exist. The target file is the file that will contain the translations that you make. You can load an existing target file in another language and browse the keys in order to get an idea of how the program works.

A new target file is created automatically when you start the application for the first time or if no previous session could be found. You can create a new target file or load one at any time. 

Your session is automatically saved while you are working and will automatically load when you restart the application. However, it is probably a good idea to save the target file before closing this application. 

Click 'Save target file' and enter a filename, it will automatically be given the 'properties' extension. It is probably best to name the file after the language that it is translated to.
