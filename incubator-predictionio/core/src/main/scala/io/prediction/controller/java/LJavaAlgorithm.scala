/** Copyright 2015 TappingStone, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package io.prediction.controller.java

import io.prediction.controller.LAlgorithm

import scala.reflect.ClassTag

/** Base class of a Java local algorithm. Refer to [[LAlgorithm]] for documentation.
  *
  * @tparam PD Prepared data class.
  * @tparam M Trained model class.
  * @tparam Q Input query class.
  * @tparam P Output prediction class.
  * @group Algorithm
  */
abstract class LJavaAlgorithm[PD, M, Q, P]
  extends LAlgorithm[PD, M, Q, P]()(ClassTag.AnyRef.asInstanceOf[ClassTag[M]])
