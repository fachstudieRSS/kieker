/***************************************************************************
 * Copyright 2014 Kieker Project (http://kieker-monitoring.net)
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
 ***************************************************************************/

package kieker.panalysis.base;

import java.util.List;

/**
 * @author Christian Wulf
 * 
 * @since 1.10
 */
public class MethodCallPipe<T> extends AbstractPipe<T, MethodCallPipe<T>> {

	private IStage targetStage;
	private T storedToken;

	public MethodCallPipe(final T initialToken) {
		this.storedToken = initialToken;
	}

	public MethodCallPipe() {
		this.storedToken = null;
	}

	@Override
	protected void putInternal(final T token) {
		this.storedToken = token;
		this.targetStage.execute();
	}

	@Override
	protected T tryTakeInternal() {
		final T temp = this.storedToken;
		this.storedToken = null;
		return temp;
	}

	public T read() {
		return this.storedToken;
	}

	public void putMultiple(final List<T> items) {
		throw new IllegalStateException("Putting more than one element is not possible. You tried to put " + items.size() + " items.");
	}

	public List<?> tryTakeMultiple(final int numItemsToTake) {
		throw new IllegalStateException("Taking more than one element is not possible. You tried to take " + numItemsToTake + " items.");
	}

	@Override
	public <S extends ISink<S>> MethodCallPipe<T> target(final S targetStage, final IInputPort<S, T> targetPort) {
		this.targetStage = targetStage;
		return super.target(targetStage, targetPort);
	}

	@Override
	public <S extends ISink<S>> MethodCallPipe<T> target(final IInputPort<S, T> targetPort) {
		this.targetStage = targetPort.getOwningStage();
		return super.target(targetPort);
	}

	public void copyAllOtherPipes(final List<MethodCallPipe<T>> pipesOfGroup) {
		// is not needed in a synchronous execution
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = (prime * result) + ((this.targetStage == null) ? 0 : this.targetStage.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		@SuppressWarnings("rawtypes")
		final MethodCallPipe other = (MethodCallPipe) obj;
		if (this.targetStage == null) {
			if (other.targetStage != null) {
				return false;
			}
		} else if (!this.targetStage.equals(other.targetStage)) {
			return false;
		}
		return true;
	}

}
