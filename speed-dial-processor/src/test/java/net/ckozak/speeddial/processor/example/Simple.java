/*
 * (c) Copyright 2022 Carter Kozak
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

package net.ckozak.speeddial.processor.example;

import net.ckozak.speeddial.annotation.SpeedDial;

import java.io.IOException;

public interface Simple extends Runnable {

    @SpeedDial(common = {SimpleOne.class, SimpleTwo.class})
    void poke();

    @SpeedDial(target = Runnable.class, common = SimpleOne.class)
    String ping();

    @SpeedDial(target = Runnable.class, common = SimpleOne.class)
    <T> T complex(T first, T second, String third);

    @SpeedDial(common = SimpleOne.class)
    void throwing() throws IOException;

    final class SimpleOne implements Simple {

        @Override
        public void poke() {
        }

        @Override
        public String ping() {
            return "pong";
        }

        @Override
        public <T> T complex(T first, T second, String third) {
            return first;
        }

        @Override
        public void throwing() throws IOException {
            throw new IOException();
        }

        @Override
        public void run() {

        }
    }

    final class SimpleTwo implements Simple {
        @Override
        public void poke() {
        }

        @Override
        public String ping() {
            return "pong";
        }

        @Override
        public <T> T complex(T first, T second, String third) {
            return first;
        }

        @Override
        public void throwing() throws IOException {
            throw new IOException();
        }

        @Override
        public void run() {

        }
    }
}
