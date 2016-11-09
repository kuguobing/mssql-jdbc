//---------------------------------------------------------------------------------------------------------------------------------
// File: SQLServerXAConnection.java
//
//
// Microsoft JDBC Driver for SQL Server
// Copyright(c) Microsoft Corporation
// All rights reserved.
// MIT License
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files(the ""Software""), 
//  to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
//  and / or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions :
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
//  IN THE SOFTWARE.
//---------------------------------------------------------------------------------------------------------------------------------
 
 
package com.microsoft.sqlserver.jdbc;

import javax.sql.*;
import java.sql.*;
import javax.transaction.xa.XAResource;
import java.util.logging.*;
import java.util.*;



/**
* SQLServerXAConnection provides JDBC connections that can participate in distributed (XA)
* transactions.
*/
public final class SQLServerXAConnection extends SQLServerPooledConnection implements XAConnection
{

	// NB These instances are not used by applications, only by the app server who is
	// providing the connection pool and transactional processing to the application.
	// That app server is the one who should restrict commit/rollback on the connections
	// it issues to applications, not the driver. These instances can and must commit/rollback
	private SQLServerXAResource XAResource;
    private SQLServerConnection physicalControlConnection; 
	private Logger xaLogger;

	/*L0*/ SQLServerXAConnection(SQLServerDataSource ds, String user, String pwd) throws java.sql.SQLException 
	{
		super(ds, user, pwd);
        // Grab SQLServerXADataSource's static XA logger instance.
                xaLogger = SQLServerXADataSource.xaLogger;
        SQLServerConnection con =  getPhysicalConnection();

        Properties controlConnectionProperties = (Properties)con.activeConnectionProperties.clone();
        // Arguments to be sent as unicode always to the server, as the stored procs always write unicode chars as out param. 
        controlConnectionProperties.setProperty(SQLServerDriverBooleanProperty.SEND_STRING_PARAMETERS_AS_UNICODE.toString(), "true"); 
		controlConnectionProperties.remove(SQLServerDriverStringProperty.SELECT_METHOD.toString());

		if (xaLogger.isLoggable(Level.FINER))
			xaLogger.finer("Creating an internal control connection for" +  toString());
		physicalControlConnection = new SQLServerConnection(toString());
		physicalControlConnection.connect(controlConnectionProperties, null);
		if (xaLogger.isLoggable(Level.FINER))
			xaLogger.finer("Created an internal control connection" + physicalControlConnection.toString()+  " for " + toString()+ " Physical connection:" 
			+ getPhysicalConnection().toString());

		
		if (xaLogger.isLoggable(Level.FINER))
			xaLogger.finer(ds.toString() + " user:"+user);
	}

	/*L0*/ public synchronized XAResource getXAResource() throws java.sql.SQLException 
	{
		// All connections handed out from this physical connection have a common XAResource
		// for transaction control. IE the XAResource is one to one with the physical connection.

		if (XAResource == null)
			XAResource = new SQLServerXAResource(getPhysicalConnection(), physicalControlConnection, 
			                        toString());
		return XAResource;
	}
    /**
    * Closes the physical connection that this PooledConnection object represents.
    */
    public void close() throws SQLException 
    {
        synchronized(this)
        {
            if (XAResource != null)
            {
                XAResource.close();
                XAResource = null;
            }
            if(null != physicalControlConnection)
            {
                physicalControlConnection.close();
                physicalControlConnection = null;                
            }
        }
        super.close();
    }

}
