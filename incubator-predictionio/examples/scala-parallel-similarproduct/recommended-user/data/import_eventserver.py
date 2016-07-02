"""
Import sample data for recommended user engine
"""

import predictionio
import argparse
import random

SEED = 3

def import_events(client):
  random.seed(SEED)
  count = 0
  print client.get_status()
  print "Importing data..."

  # generate 10 users, with user ids u1,u2,....,u50
  user_ids = ["u%s" % i for i in range(1, 51)]
  for user_id in user_ids:
    print "Set user", user_id
    client.create_event(
      event="$set",
      entity_type="user",
      entity_id=user_id
    )
    count += 1

  # each user randomly follows 10 users
  for user_id in user_ids:
    for followed_user in random.sample(user_ids, 10):
      print "User", user_id ,"follows User", followed_user
      client.create_event(
        event="follow",
        entity_type="user",
        entity_id=user_id,
        target_entity_type="user",
        target_entity_id=followed_user
      )
      count += 1

  print "%s events are imported." % count

if __name__ == '__main__':
  parser = argparse.ArgumentParser(
    description="Import sample data for recommended user engine")
  parser.add_argument('--access_key', default='invald_access_key')
  parser.add_argument('--url', default="http://localhost:7070")

  args = parser.parse_args()
  print args

  client = predictionio.EventClient(
    access_key=args.access_key,
    url=args.url,
    threads=5,
    qsize=500)
  import_events(client)
