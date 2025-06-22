#!/bin/sh
set -x
runOnly=false
install=true
scp=false

#maxMem="0.8"    # ~80% of free
absoluteMem="6" # G
paralell="true"
gradleStopOnExit=false # false wont work with maxMem due to spawning new daemon
maxThreads="10"        # Specify or use core count
build=false
deviceSpecified=false
deviceArg=""

remote_ip="11.0.0.3"
remote_ip_fallback="10.0.0.21"
remote_path="./Documents/Skola/ht23-projects-dva232-ht23-group9"
remote_output="$remote_path/PetApp/app/build/outputs/apk/debug/app-debug.apk"
remote_script="$remote_path/build.sh"
remote_args=$(echo "$@" | sed 's/scp//g' | sed 's/build//g')
args=${@}

tmux_attach=true

app="com.example.petfinder"
start="$app/com.example.petfinder.MainActivity"
path=$(echo "$0" | sed 's/build.sh/PetApp/g')

output="$path/app/build/outputs/apk/debug/app-debug.apk"
previous_instance=""
main() {
	clear
	if ! "$runOnly" || "$build"; then build || exit 1; fi

	if $scp; then
		sendScp || exit 1
		exit 0
	fi

	getNumDevices || exit 1

	chooseDevice || exit 1

	printf "\nChoosing device: %s\n" "$device"

	printf "\nTarget APK:      %s\n" "$output"

	if ! $runOnly || $install; then adbInstall || exit 1; fi

	adbRun

	waitStartup || exit 1

	tmuxSplit

	startLogcat "$device" || exit 1

	exit 0
}

sendScp() {
	if ping -q -c 1 -W 1 "$remote_ip" 2>/dev/null 1>&2; then
		ip="$remote_ip"
	elif ping -q -c 1 -W 1 "$remote_ip_fallback" 2>/dev/null 1>&2; then
		ip="$remote_ip_fallback"
	else
		return 1
	fi

	scp "$output" "$ip:$remote_output" || return 1
	ssh "$ip" $remote_script "headless" $remote_args || return 1
}

build() {

	if [ -z "$absoluteMem" ] || [ -n "$maxMem" ] && "$gradleStopOnExit"; then
		freeMem=$(free -h -m | tr -s " " | awk 'NR==2 {print $7}' | sed 's/[^0-9.,]//g' | sed 's/,/./g')
		mem=$(echo "$freeMem * $maxMem" | bc)
		mem=$(LC_NUMERIC="en_US.UTF-8" printf "%.0f" "$mem")
	else
		mem="$absoluteMem"
	fi

	if [ -z "$maxThreads" ]; then
		maxThreads=$(grep -c processor /proc/cpuinfo)
	fi
	#--daemon --build-cache
	if ! (cd "$path" && gradle build -D "org.gradle.jvmargs=-Xmx$mem""G" -D "org.gradle.parallel=$paralell" -D "org.gradle.workers.max=$maxThreads"); then
		printf "\nCould not build %s\n\n" "$path"
		if $gradleStopOnExit; then
			gradle --stop 1>/dev/null 2>&1
		fi
		return 1
	fi

	printf "\nBuild successfull!\n\tApp: %s\n\tOutput: %s\n" "$app" "$output"

	if $gradleStopOnExit; then
		gradle --stop 1>/dev/null
	fi

	return 0
}

waitStartup() {
	retries=30 # 3 sec
	#while ! (adb -s "$device" shell pidof "$app" 1>/dev/null); do
	pid=$(adb -s "$device" shell pidof "$app")
	while [ -z "$pid" ] || [ "$previous_instance" = "$pid" ]; do
		sleep 0.1
		retries=$((retries - 1))
		if [ "$retries" -eq 0 ]; then
			printf "\nWait for pid timed out\n"
			return 1
		fi
		pid=$(adb -s "$device" shell pidof "$app")
	done
	printf "\n$app is running on\n\tPID: %s\n" "$pid"
	return 0
}

getNumDevices() {
	devices=$(adb devices | grep -v "List" | sed '/^$/d' | wc -l)
	if [ "$devices" -le 0 ]; then
		printf "\nadb not connected\n"
		return 1
	fi
	return 0
}

chooseDevice() {
	printf "\nDevices found:\n"
	adb devices | grep -v "List" | sed '/^$/d' | awk ' {printf("\t%d:  %s\n", NR, $0) }'

	if $deviceSpecified; then
		device=$(adb devices | grep "$deviceArg" 2>/dev/null | sed '/^$/d' 2>/dev/null | tr -s " " 2>/dev/null | sed 's/\sdevice//g' 2>/dev/null)
		[ -n "$device" ] && return 0

		device=$(adb devices | grep -i "$deviceArg" 2>/dev/null | sed '/^$/d' 2>/dev/null | tr -s " " 2>/dev/null | sed 's/\sdevice//g' 2>/dev/null)
		[ -n "$device" ] && return 0

		printf "Could not find %s, searching for other devices\n" "$deviceArg"
	fi

	device=$(adb devices | grep -v "List" | sed '/^$/d' | awk 'NR == 0 {print $0}' | tr -s " " | sed 's/\sdevice//g')
	[ -n "$device" ] && return 0
	[ "$devices" -lt 1 ] && return 1

	device=$(adb devices | grep -v "List" | sed '/^$/d' | awk 'NR == 1 {print $0}' | tr -s " " | sed 's/\sdevice//g')
	[ -n "$device" ] && return 0
	[ "$devices" -lt 2 ] && return 1

	device=$(adb devices | grep -v "List" | sed '/^$/d' | awk 'NR == 2 {print $1}' | tr -s " " | sed 's/\sdevice//g')
	[ -n "$device" ] && return 0

	return 1
}

uninstallApp() {
	if ! (adb -s "$device" uninstall "$app" 1>/dev/null); then
		printf "\nCould not uninstall %s from $device\n" "$app"
		return 1
	fi
	return 0

}

adbRun() {

	previous_instance=$(adb -s "$device" shell pidof "$app")
	printf "\n\nLaunching:\n\t%s\nOn:\n\t%s\n" "$start" "$device"
	adb -s "$device" shell am start -n "$start" 1>/dev/null || return 1
	return 0
}

adbInstall() {
	if (adb -s "$device" install "$output" 1>/dev/null); then
		printf "\nApp installed to %s\n" "$device"
	else
		uninstallApp || return 1
		adb -s "$device" install "$output" 1>/dev/null || return 1
		printf "\nApp installed to %s\n" "$device"
	fi

}

tmuxSplit() {
	if [ -z "${TMUX}" ]; then
		session=$(tmux list-sessions | grep -i "gradle" | cut -d ":" -f 1)
		if [ -z "$session" ]; then
			session="Gradle"
			tmux new-session -s "$session" -d
			wait $!
		fi
	fi

	currentPanes=$(tmux list-panes | wc -l)
	[ "$currentPanes" -le 1 ] && tmux split-window -v -t "$session":

	#p1Height=$(tmux display -p -t "$session":0.0 "#{pane_height}")
	#p2Height=$(tmux display -p -t "$session":0.1 "#{pane_height}")
	p1Height=$(tmux display -p -t "$session":.0 "#{pane_height}")
	p2Height=$(tmux display -p -t "$session":.1 "#{pane_height}")

	lines=$((p1Height + p2Height))
	logPanesLines=$((lines / 3))
	#tmux resize-pane -t "$session":0.1 -y "$logPanesLines"
	tmux resize-pane -t "$session":.1 -y "$logPanesLines"

	#tmux send-keys -t "$session":0.1 C-c
	#tmux send-keys -t "$session":.0 C-c
	tmux send-keys -t "$session":.1 C-c

}

startLogcat() {
	#tmux send-keys -t "$session":.0 C-c
	tmux send-keys -t "$session":.1 C-c
	tmux send-keys -t "$session:.1" "adb -s $device logcat --pid=$pid" Enter || return 1
	tmux send-keys -t "$session:.0" "#Running with arguments: $args" Enter

	if [ -z "${TMUX}" ] && $tmux_attach; then
		tmux last-pane -t "$session"
		if $tmux_attach; then
			tmux a -t "$session"
			return $?
		fi
	fi

}

if [ -z "$1" ]; then
	runOnly=true
	install=true
	build=true
fi

for arg in "$@"; do
	[ "$arg" = run ] && runOnly=true && continue
	[ "$arg" = install ] && install=true && continue
	[ "$arg" = scp ] && scp=true && continue
	[ "$arg" = headless ] && tmux_attach=false && continue
	[ "$arg" = build ] && build=true && continue
	[ "$arg" = device ] && deviceSpecified=true && continue
	[ $deviceSpecified = true ] && deviceArg="$arg" && continue
done

main
