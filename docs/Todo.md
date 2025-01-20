Python program similar to PDF arranger for splitting of docs

Change output path to be within hierarchy.

Refactor to only use 1 set of images with lower tolerance.

Further refactor into seperate files under SystmOne_Automation_Proj\src\main\java\systmone\automation\

Solve the "1 doc vs 100 docs" problem.

Doesnt work when only monitor 2.

When it doesnt detect movement on the first check of the thumb, it never rechecks the position to see if its changed. It just constantly compares. - Has no reason as to why, probable race condition?