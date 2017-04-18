/*
Copyright (C) 2017 Jeff Thompson

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.nuvl.nuvlworld;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.nuvl.argue.aba_plus.Sentence;

/**
 * A NuvlWorldStore holds a set of aba_plus Sentences plus other cached values
 * needed by the application.
 * @author Jeff Thompson, jeff@thefirst.org
 */
public class NuvlWorldStore {
  public NuvlWorldStore()
  {
  }

  /** key: predicate, value: set of Sentence. */
  public final Map<String, Set<Sentence>> sentencesByPredicate_ = new HashMap<>();
}
