# Replace values here as needed
cwd=$(pwd)
src_cfg="$cwd"/.circleci/config.yml
intermediate_cfg="$cwd"/.circleci/config_processed.yml # Have your VCS ignore this -- don't commit it
user_cfg="$HOME"/.circleci
execute_args="--job test" # Whatever args you intended to pass to execute
docker_sock=/var/run/docker.sock

# Note that in this command the volume mount source for
# /tmp/local_build_config.yml is absolute, and should exist
circleci config process $src_cfg > $intermediate_cfg \
  && docker run -it --rm -v $docker_sock:$docker_sock \
      -v $intermediate_cfg:/tmp/local_build_config.yml \
      -v "$cwd:$cwd" \
      -v "$user_cfg":/root/.circleci \
      --workdir "$cwd" \
      circleci/picard@sha256:ecbf254faa4688a254f9da7d2b576e46094c09f77d478cf091ca49cf587e8354 \
      circleci build --config /tmp/local_build_config.yml \
      $execute_args
