/*
 * Copyright (C) 2015 Philipp Nanz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package com.github.philippn.springremotingautoconfigure.sample.server;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.github.philippn.springremotingautoconfigure.sample.TimeService;

/**
 * @author Philipp
 */
@Service
public class TimeServiceImpl implements TimeService {

	/* (non-Javadoc)
	 * @see com.github.philippn.springremotingautoconfigure.sample.TimeService#serverTime()
	 */
	@Override
	public LocalDateTime serverTime() {
		return LocalDateTime.now();
	}
}
