-- Align enum-backed columns to NVARCHAR to match Hibernate's NVARCHAR handling on SQL Server

IF OBJECT_ID('books', 'U') IS NOT NULL
BEGIN
  -- Step 1: Drop the default constraint if it exists
  DECLARE @default_constraint_name_books NVARCHAR(255);
  SELECT @default_constraint_name_books = dc.name
  FROM sys.default_constraints dc
  JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
  WHERE dc.parent_object_id = OBJECT_ID('dbo.books') AND c.name = 'approval_status';

  IF @default_constraint_name_books IS NOT NULL
  BEGIN
      EXEC('ALTER TABLE dbo.books DROP CONSTRAINT ' + @default_constraint_name_books);
  END;

  -- Step 2: Drop all CHECK constraints related to the column using a cursor
  DECLARE @check_constraint_name NVARCHAR(255);
  DECLARE constraint_cursor CURSOR FOR
      SELECT cc.name
      FROM sys.check_constraints cc
      WHERE cc.parent_object_id = OBJECT_ID('dbo.books')
        AND COL_NAME(cc.parent_object_id, cc.parent_column_id) = 'approval_status';

  OPEN constraint_cursor;
  FETCH NEXT FROM constraint_cursor INTO @check_constraint_name;

  WHILE @@FETCH_STATUS = 0
  BEGIN
      DECLARE @sql_drop_check NVARCHAR(MAX);
      SET @sql_drop_check = 'ALTER TABLE dbo.books DROP CONSTRAINT ' + QUOTENAME(@check_constraint_name);
      EXEC sp_executesql @sql_drop_check;
      FETCH NEXT FROM constraint_cursor INTO @check_constraint_name;
  END;

  CLOSE constraint_cursor;
  DEALLOCATE constraint_cursor;

  -- Step 3: Alter the column type
  ALTER TABLE dbo.books
  ALTER COLUMN approval_status NVARCHAR(20) NOT NULL;
END;
