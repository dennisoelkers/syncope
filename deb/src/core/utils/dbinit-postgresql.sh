#!/bin/sh
# Licensed under Apache License 2.0 - http://www.apache.org/licenses/LICENSE-2.0
# This script creates a syncope database and user with password.
# Supply DB_USERNAME and DB_PASSWORD as environment variables
#

echo "Creating PostgreSQL role for syncope. If already exists, the error message should be ignored"
su - postgres -c "psql -c \"create role $SYNCOPE_USER with login password '$SYNCOPE_PASS'\""
echo "Creating PostgreSQL database for syncope. If already exists, the error message should be ignored"
su - postgres -c "psql -c \"create database syncope owner $SYNCOPE_USER\""
