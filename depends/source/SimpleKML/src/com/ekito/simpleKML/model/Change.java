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
 * Modifies the values in an element that has already been loaded with a {@link NetworkLink}. Within the Change element, the child to be modified must include a targetId attribute that references the original element's id.
 * This update can be considered a "sparse update": in the modified element, only the values listed in {@link Change} are replaced; all other values remained untouched. When {@link Change} is applied to a set of coordinates, the new coordinates replace the current coordinates.
 * Children of this element are the element(s) to be modified, which are identified by the targetId attribute.
 */
public class Change extends UpdateProcess {}
