/**
 * Copyright 2012 Ekito - http://www.ekito.fr/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ekito.simpleKML.model;

/**
 * Adds new elements to a Folder or Document that has already been loaded via a {@link NetworkLink}. The targetHref element in {@link Update} specifies the URL of the .kml or .kmz file that contained the original Folder or Document. Within that file, the Folder or Document that is to contain the new data must already have an explicit id defined for it. This id is referenced as the targetId attribute of the Folder or Document within {@link Create} that contains the element to be added.
 * Once an object has been created and loaded into Google Earth, it takes on the URL of the original parent Document of Folder. To perform subsequent updates to objects added with this Update/Create mechanism, set <targetHref> to the URL of the original Document or Folder (not the URL of the file that loaded the intervening updates).
 */
public class Create extends UpdateProcess {}
