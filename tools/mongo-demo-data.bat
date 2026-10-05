@echo off
REM ==========================================================================
REM mongo-demo-data.bat - Sinh DU LIEU GIAO DICH THAT cho demo / bao cao
REM
REM   Sinh: carts, orders (+subOrders+items), reviews, coupons,
REM         payment_transactions, order_returns, support_tickets,
REM         notifications (+deliveries), daily_stats, association_rules,
REM         activity_log (time-series)
REM   Va $merge lai: books.rating/topReviews/stats/tags, users.stats/ml(RFM),
REM         seller_shops.stats, categories.bookCount, books.boughtTogether
REM
REM   Chay SAU:  tools\mongo-import-books.bat
REM              tools\mongo-import-sellers.bat
REM              tools\mongo-demo-accounts.bat
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

echo ============================================================
echo BOOKOM - Sinh du lieu giao dich demo
echo   URI : %MONGO_URI%
echo   File: %DB_SCRIPTS%\13_demo_transactions.js
echo ============================================================
echo.

"%MONGOSH%" "%MONGO_URI%" --quiet --file "%DB_SCRIPTS%\13_demo_transactions.js"

endlocal
