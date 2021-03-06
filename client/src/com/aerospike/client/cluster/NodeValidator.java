/*
 * Copyright 2012-2015 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.client.cluster;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Host;
import com.aerospike.client.Info;
import com.aerospike.client.Log;
import com.aerospike.client.admin.AdminCommand;
import com.aerospike.client.util.Util;

public final class NodeValidator {
	String name;
	Host[] aliases;
	InetSocketAddress address;
	boolean hasBatchIndex;
	boolean hasReplicasAll;
	boolean hasDouble;

	public NodeValidator(Cluster cluster, Host host) throws Exception {
		try {
			InetAddress[] addresses = InetAddress.getAllByName(host.name);
			aliases = new Host[addresses.length];
			
			for (int i = 0; i < addresses.length; i++) {
				aliases[i] = new Host(addresses[i].getHostAddress(), host.port);
			}
		}
		catch (UnknownHostException uhe) {
			throw new AerospikeException.Connection("Invalid host: " + host);
		}

		Exception exception = null;
		
		for (int i = 0; i < aliases.length; i++) {
			Host alias = aliases[i];
			
			try {
				InetSocketAddress address = new InetSocketAddress(alias.name, alias.port);
				Connection conn = new Connection(address, cluster.getConnectionTimeout());
				
				try {			
					if (cluster.user != null) {
						AdminCommand command = new AdminCommand();
						command.authenticate(conn, cluster.user, cluster.password);
					}
					HashMap<String,String> map = Info.request(conn, "node", "features");
					String nodeName = map.get("node");
					
					if (nodeName != null) {
						this.name = nodeName;
						this.address = address;
						setFeatures(map);
						return;
					}
				}
				finally {
					conn.close();
				}
			}
			catch (Exception e) {
				// Try next address.
				if (Log.debugEnabled()) {
					Log.debug("Alias " + alias + " failed: " + Util.getErrorMessage(e));
				}

				if (exception == null)
				{
					exception = e;
				}
			}	
		}		

		if (exception == null)
		{
			throw new AerospikeException.Connection("Failed to find addresses for " + host);
		}
		throw exception;
	}
	
	private void setFeatures(HashMap<String,String> map) {
		try {
			String features = map.get("features");
			int begin = 0;
			int end = 0;
			int len;
			
			while (end < features.length() && !(this.hasDouble && this.hasBatchIndex && this.hasReplicasAll)) {
				end = features.indexOf(';', begin);
				
				if (end < 0) {
					end = features.length();
				}
				len = end - begin;
				
				if (features.regionMatches(begin, "float", 0, len)) {
					this.hasDouble = true;
				}

				if (features.regionMatches(begin, "batch-index", 0, len)) {
					this.hasBatchIndex = true;
				}
				
				if (features.regionMatches(begin, "replicas-all", 0, len)) {
					this.hasReplicasAll = true;
				}	        	
				begin = end + 1;
			}        
		}
		catch (Exception e) {
			// Unexpected exception. Use defaults.
		}
	}
	
	/*
	private static final class BuildVersion {
		private final int major;
		private final int minor;
		private final int revision;
		
		private BuildVersion(String version) {
			int begin = 0;			
			int i = begin;
			int max = version.length();
			
			while (i < max) {
				if (! Character.isDigit(version.charAt(i))) {
					break;
				}
				i++;
			}
			
			major = (i > begin)? Integer.parseInt(version.substring(begin, i)) : 0;
			begin = ++i;
			
			while (i < max) {
				if (! Character.isDigit(version.charAt(i))) {
					break;
				}
				i++;
			}

			minor = (i > begin)? Integer.parseInt(version.substring(begin, i)) : 0;
			begin = ++i;
			
			while (i < max) {
				if (! Character.isDigit(version.charAt(i))) {
					break;
				}
				i++;
			}
			
			revision = (i > begin)? Integer.parseInt(version.substring(begin, i)) : 0;
		}

		private boolean hasReplicasAll() {
			// Check for "replicas-all" info protocol support (version >= 3.5.9).
			return isGreaterEqual(3, 5, 9);
		}
		
		private boolean isGreaterEqual(int v1, int v2, int v3) {
			return major > v1 || (major == v1 && (minor > v2 || (minor == v2 && revision >= v3)));
		}
	}
	*/
}
